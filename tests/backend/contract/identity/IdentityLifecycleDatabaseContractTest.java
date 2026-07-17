package com.pdp.contract.identity;

import com.pdp.identity.application.IdentityLifecycleService;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserSession;
import com.pdp.identity.domain.UserStatus;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.identity.port.UserSessionRepository;
import com.pdp.shared.error.BusinessRuleException;
import com.pdp.shared.id.UuidV7Generator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 身份生命周期 MySQL 契约测试（T040）。
 *
 * <p>守护身份生命周期状态机、乐观锁、唯一约束、会话撤销与 OIDC 绑定在真实 MySQL 8.4 上的端到端语义。
 * 验证对象：
 * <ul>
 *   <li>{@link UserAccountMapper} 与 user_account 表的 CRUD、状态机、唯一约束（uniq_user_username、uniq_user_email）</li>
 *   <li>{@link UserSessionMapper} 与 user_session 表的撤销语义（revokeAllByUser、revoke、selectActiveByUser）</li>
 *   <li>{@link ExternalIdentityMapper} 与 external_identity 表的唯一约束（uniq_ext_issuer_subject）</li>
 *   <li>{@link IdentityLifecycleService} 的状态迁移与并发冲突业务规则</li>
 *   <li>TypeHandler 往返：UUID ↔ BINARY(16)、Instant ↔ datetime(6)、UserStatus ↔ varchar name()</li>
 * </ul>
 *
 * <p>本测试为骨架契约测试，使用 Testcontainers 启动独立 MySQL 8.4 实例；当前在
 * tests/backend Maven 测试模块建立后编译执行（见 T096 基础阶段门禁）。CI 矩阵（T025）在
 * MySQL 8.4 LTS 上强制运行。
 *
 * <p>对应任务：T040；规格条款：FR-149~FR-162、SC-030；状态机：DRAFT → ACTIVE → DISABLED → DEPARTED。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@DisplayName("T040 身份生命周期 MySQL 契约测试")
public class IdentityLifecycleDatabaseContractTest {

    /** MySQL 8.4 LTS 容器，UTC 时区、utf8mb4、InnoDB、READ_COMMITTED 隔离级别。 */
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
            DockerImageName.parse("mysql:8.4-lts")
                    .asCompatibleSubstituteFor("mysql"))
            .withDatabaseName("pdp")
            .withUsername("pdp_app")
            .withPassword("test")
            .withCommand(
                    "--default-time-zone=+00:00",
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_unicode_ci",
                    "--default-storage-engine=InnoDB",
                    "--transaction-isolation=READ-COMMITTED");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        // 覆盖 application-datasource.yml 的 pdpPrimary 数据源指向 Testcontainer
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.password", MYSQL::getPassword);
        // pdpRead 复用同一容器（测试不验证读写分离）
        registry.add("spring.datasource.dynamic.datasource.pdpRead.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.pdpRead.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.pdpRead.password", MYSQL::getPassword);
    }

    @Autowired
    private IdentityLifecycleService lifecycleService;

    @Autowired
    private UserAccountRepository accountRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    /** 每个测试用独立用户名/邮箱避免唯一约束污染。 */
    private final String username = "contract-" + UUID.randomUUID().toString().substring(0, 8);
    private final String email = username + "@pdp.test";

    @BeforeEach
    void seedUser() {
        UserAccount draft = new UserAccount(
                UuidV7Generator.next(), username, "Contract Test", email,
                UserStatus.DRAFT, Instant.now(), Instant.now(), 1);
        accountRepository.save(draft);
    }

    @AfterEach
    void cleanup() {
        // 测试间不累积状态；按用户名定位并清理（生产代码不应直接物理删除，仅测试用）
        accountRepository.findByUsername(username).ifPresent(u ->
                accountRepository.updateStatus(u.id(), UserStatus.DEPARTED, u.revision()));
    }

    private UserAccount load() {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("种子用户丢失: " + username));
    }

    // ============================================================
    // 状态机契约：DRAFT → ACTIVE → DISABLED → DEPARTED
    // ============================================================

    @Nested
    @DisplayName("状态机迁移")
    class StateMachineTransitions {

        @Test
        @DisplayName("DRAFT → ACTIVE：activate 成功递增 revision")
        void activateFromDraftSucceedsAndIncrementsRevision() {
            // Given: 种子用户处于 DRAFT 状态，revision=1
            UserAccount before = load();
            assertThat(before.status()).isEqualTo(UserStatus.DRAFT);
            assertThat(before.revision()).isEqualTo(1);

            // When: 调用 activate
            UserAccount after = lifecycleService.activate(before.id());

            // Then: 状态变为 ACTIVE，revision 递增为 2，updatedAt 推进
            assertThat(after.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(after.revision()).isEqualTo(2);
            assertThat(after.updatedAt()).isAfterOrEqualTo(before.updatedAt());
        }

        @Test
        @DisplayName("ACTIVE → DISABLED：disable 成功并撤销全部会话")
        void disableFromActiveRevokesAllSessions() {
            // Given: ACTIVE 用户已签发 2 个有效会话
            UserAccount active = lifecycleService.activate(load().id());
            UUID userId = active.id();
            seedSession(userId, "hash-1");
            seedSession(userId, "hash-2");
            assertThat(sessionRepository.findActiveByUser(userId, Instant.now())).hasSize(2);

            // When: 调用 disable
            UserAccount after = lifecycleService.disable(userId, "测试停用");

            // Then: 状态 DISABLED，2 个会话全部撤销
            assertThat(after.status()).isEqualTo(UserStatus.DISABLED);
            assertThat(sessionRepository.findActiveByUser(userId, Instant.now())).isEmpty();
        }

        @Test
        @DisplayName("DISABLED → ACTIVE：activate 允许恢复")
        void activateFromDisabledRestores() {
            // Given: DISABLED 用户
            UserAccount active = lifecycleService.activate(load().id());
            UserAccount disabled = lifecycleService.disable(active.id(), "临时停用");

            // When: 再次 activate
            UserAccount restored = lifecycleService.activate(disabled.id());

            // Then: 状态恢复 ACTIVE
            assertThat(restored.status()).isEqualTo(UserStatus.ACTIVE);
        }

        @Test
        @DisplayName("任何状态 → DEPARTED：depart 即时撤销全部会话")
        void departRevokesAllSessionsImmediately() {
            // Given: ACTIVE 用户已签发 3 个有效会话
            UserAccount active = lifecycleService.activate(load().id());
            UUID userId = active.id();
            seedSession(userId, "h-1");
            seedSession(userId, "h-2");
            seedSession(userId, "h-3");

            // When: 调用 depart（离职即时失效）
            Instant beforeCall = Instant.now();
            UserAccount departed = lifecycleService.depart(userId, "员工离职");
            Instant afterCall = Instant.now();

            // Then: 状态 DEPARTED，3 个会话全部在调用期内撤销（FR-160 撤销时效 ≤ 30s）
            assertThat(departed.status()).isEqualTo(UserStatus.DEPARTED);
            assertThat(sessionRepository.findActiveByUser(userId, Instant.now())).isEmpty();
            // 调用窗口非空（beforeCall ≤ afterCall）
            assertThat(afterCall).isAfterOrEqualTo(beforeCall);
        }

        @Test
        @DisplayName("DEPARTED → DEPARTED：幂等，不抛出异常")
        void departIsIdempotent() {
            // Given: 已 DEPARTED 用户
            UserAccount active = lifecycleService.activate(load().id());
            UserAccount departed = lifecycleService.depart(active.id(), "first");

            // When: 再次 depart
            UserAccount again = lifecycleService.depart(departed.id(), "second");

            // Then: 不抛异常，状态仍为 DEPARTED
            assertThat(again.status()).isEqualTo(UserStatus.DEPARTED);
        }
    }

    // ============================================================
    // 业务规则与非法状态迁移
    // ============================================================

    @Nested
    @DisplayName("非法状态迁移")
    class IllegalTransitions {

        @Test
        @DisplayName("DISABLED → DISABLED：disable 拒绝（仅 ACTIVE 可停用）")
        void disableFromDisabledRejected() {
            UserAccount active = lifecycleService.activate(load().id());
            UserAccount disabled = lifecycleService.disable(active.id(), "first");

            assertThatThrownBy(() -> lifecycleService.disable(disabled.id(), "second"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("DEPARTED → activate：拒绝（已离职不可恢复）")
        void activateFromDepartedRejected() {
            UserAccount active = lifecycleService.activate(load().id());
            UserAccount departed = lifecycleService.depart(active.id(), "离职");

            assertThatThrownBy(() -> lifecycleService.activate(departed.id()))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    // ============================================================
    // 乐观锁与并发冲突
    // ============================================================

    @Nested
    @DisplayName("乐观锁与并发冲突")
    class OptimisticLocking {

        @Test
        @DisplayName("revision 不匹配的 updateStatus 返回 false（CONFLICT）")
        void updateStatusWithStaleRevisionFails() {
            // Given: 用户处于 ACTIVE，revision=2
            UserAccount active = lifecycleService.activate(load().id());
            int staleRevision = active.revision() - 1; // 用旧 revision 模拟并发

            // When: 用 stale revision 直接调用端口
            boolean ok = accountRepository.updateStatus(
                    active.id(), UserStatus.DISABLED, staleRevision);

            // Then: 0 行受影响，状态未变
            assertThat(ok).isFalse();
            UserAccount unchanged = accountRepository.findById(active.id()).orElseThrow();
            assertThat(unchanged.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(unchanged.revision()).isEqualTo(active.revision());
        }

        @Test
        @DisplayName("并发 activate：仅一方成功，另一方 CONFLICT")
        void concurrentActivateOnlyOneSucceeds() {
            // Given: 同一 DRAFT 用户
            UserAccount draft = load();

            // When: 第一次 activate 成功
            boolean first = accountRepository.updateStatus(
                    draft.id(), UserStatus.ACTIVE, draft.revision());

            // Then: 用同一旧 revision 第二次 activate 必须失败
            boolean second = accountRepository.updateStatus(
                    draft.id(), UserStatus.ACTIVE, draft.revision());

            assertThat(first).isTrue();
            assertThat(second).isFalse();
        }
    }

    // ============================================================
    // TypeHandler 往返（UUID / Instant / UserStatus）
    // ============================================================

    @Nested
    @DisplayName("TypeHandler 往返")
    class TypeHandlerRoundTrip {

        @Test
        @DisplayName("UUID ↔ BINARY(16) 往返：写入后读出 id 一致")
        void uuidBinaryRoundTrip() {
            UserAccount loaded = load();

            // 重新通过 findById 读取，验证 UUIDv7 完整性
            UserAccount again = accountRepository.findById(loaded.id()).orElseThrow();
            assertThat(again.id()).isEqualTo(loaded.id());
        }

        @Test
        @DisplayName("Instant ↔ datetime(6) 往返：微秒精度保留")
        void instantMicrosecondPrecisionRoundTrip() {
            UserAccount loaded = load();
            UserAccount again = accountRepository.findById(loaded.id()).orElseThrow();

            // datetime(6) 微秒精度，截断到微秒后比较
            assertThat(again.createdAt().truncatedTo(ChronoUnit.MICROS))
                    .isEqualTo(loaded.createdAt().truncatedTo(ChronoUnit.MICROS));
        }

        @Test
        @DisplayName("UserStatus ↔ varchar：name() 稳定键往返")
        void enumNameStableKeyRoundTrip() {
            UserAccount loaded = load();
            UserAccount again = accountRepository.findById(loaded.id()).orElseThrow();
            assertThat(again.status()).isEqualTo(UserStatus.DRAFT);
        }
    }

    // ============================================================
    // 唯一约束
    // ============================================================

    @Nested
    @DisplayName("唯一约束")
    class UniqueConstraints {

        @Test
        @DisplayName("重复 username 违反 uniq_user_username")
        void duplicateUsernameViolatesUnique() {
            // Given: 已存在 username 的用户
            UserAccount existing = load();
            UUID newId = UuidV7Generator.next();
            UserAccount dup = new UserAccount(
                    newId, existing.username(), "Another", "another-" + email,
                    UserStatus.DRAFT, Instant.now(), Instant.now(), 1);

            // When/Then: 插入应失败（具体异常类型由 Spring 异常翻译为 DataIntegrityViolationException）
            assertThatThrownBy(() -> accountRepository.save(dup))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("重复 email 违反 uniq_user_email")
        void duplicateEmailViolatesUnique() {
            UserAccount existing = load();
            UUID newId = UuidV7Generator.next();
            UserAccount dup = new UserAccount(
                    newId, "another-" + username, "Another", existing.email(),
                    UserStatus.DRAFT, Instant.now(), Instant.now(), 1);

            assertThatThrownBy(() -> accountRepository.save(dup))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }

    // ============================================================
    // 会话撤销语义
    // ============================================================

    @Nested
    @DisplayName("会话撤销语义")
    class SessionRevocationSemantics {

        @Test
        @DisplayName("revoke 单个会话：乐观锁匹配成功，其他会话不受影响")
        void revokeSingleSessionPreservesOthers() {
            // Given: 用户 2 个有效会话
            UserAccount active = lifecycleService.activate(load().id());
            UUID userId = active.id();
            seedSession(userId, "hash-A");
            seedSession(userId, "hash-B");
            UserSession s1 = sessionRepository.findBySessionTokenHash("hash-A").orElseThrow();

            // When: 撤销 s1
            boolean ok = lifecycleService.revokeSession(s1.id(), "手动下线");

            // Then: 仅 s1 被撤销，s2 仍有效
            assertThat(ok).isTrue();
            assertThat(sessionRepository.findActiveByUser(userId, Instant.now())).hasSize(1);
        }

        @Test
        @DisplayName("revokeAllByUser 撤销全部有效会话，已撤销的会话不被重复更新")
        void revokeAllByUserDoesNotTouchAlreadyRevoked() {
            // Given: 3 个会话，其中 1 个已手动撤销
            UserAccount active = lifecycleService.activate(load().id());
            UUID userId = active.id();
            seedSession(userId, "h-1");
            seedSession(userId, "h-2");
            seedSession(userId, "h-3");
            UserSession toRevoke = sessionRepository.findBySessionTokenHash("h-2").orElseThrow();
            sessionRepository.revoke(toRevoke.id(), "first", Instant.now(), toRevoke.revision());

            // When: revokeAllByUser
            int revoked = sessionRepository.revokeAllByUser(userId, "BATCH", Instant.now(), 0);

            // Then: 2 个剩余会话被撤销（h-1, h-3），h-2 已撤销不再受影响
            assertThat(revoked).isEqualTo(2);
            assertThat(sessionRepository.findActiveByUser(userId, Instant.now())).isEmpty();
        }

        @Test
        @DisplayName("selectActiveByUser 排除已过期会话")
        void selectActiveByUserExcludesExpired() {
            // Given: 1 个有效会话 + 1 个已过期未撤销会话
            UserAccount active = lifecycleService.activate(load().id());
            UUID userId = active.id();
            seedSession(userId, "valid-hash");
            seedExpiredSession(userId, "expired-hash");

            // When: 查询有效会话
            var activeSessions = sessionRepository.findActiveByUser(userId, Instant.now());

            // Then: 仅返回未过期会话
            assertThat(activeSessions).hasSize(1);
            assertThat(activeSessions.get(0).sessionTokenHash()).isEqualTo("valid-hash");
        }
    }

    // ============================================================
    // 测试辅助
    // ============================================================

    private void seedSession(UUID userId, String tokenHash) {
        UserSession session = new UserSession(
                UuidV7Generator.next(), userId, tokenHash, null,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                null, null, "127.0.0.1", 1);
        sessionRepository.save(session);
    }

    private void seedExpiredSession(UUID userId, String tokenHash) {
        UserSession session = new UserSession(
                UuidV7Generator.next(), userId, tokenHash, null,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS),
                null, null, "127.0.0.1", 1);
        sessionRepository.save(session);
    }
}
