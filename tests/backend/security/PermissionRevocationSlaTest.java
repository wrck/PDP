package com.pdp.security;

import com.pdp.identity.application.IdentityLifecycleService;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserSession;
import com.pdp.identity.domain.UserStatus;
import com.pdp.identity.port.UserAccountRepository;
import com.pdp.identity.port.UserSessionRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限撤销时效基线测试（T042）。
 *
 * <p>守护 FR-160：用户停用/离职后，权限撤销（会话失效、刷新凭据吊销）必须在 30 秒内完成传播。
 * 这是平台安全基线，由 {@code docs/service-levels/p1-service-catalog.md} SLI/SLO 守护。
 *
 * <p>验证维度：
 * <ul>
 *   <li>离职即时失效：{@link IdentityLifecycleService#depart} 触发 revokeAllByUser 单条 UPDATE 原子完成</li>
 *   <li>管理员强制下线：{@link IdentityLifecycleService#revokeAllSessions} 同样原子撤销</li>
 *   <li>大用户量会话撤销：单用户 100+ 会话在 30 秒内全部撤销</li>
 *   <li>撤销后查询：findActiveByUser 立即返回空集（无传播延迟）</li>
 *   <li>撤销时间戳：revoked_at 落在调用窗口内（单库事务一致性）</li>
 * </ul>
 *
 * <p>本测试为骨架契约测试，使用 Testcontainers 启动独立 MySQL 8.4 实例；当前在
 * tests/backend Maven 测试模块建立后编译执行。CI 矩阵（T025）在 MySQL 8.4 LTS 上强制运行，
 * 性能基线由 {@code docs/service-levels/p1-service-catalog.md} 定义。
 *
 * <p>对应任务：T042；规格条款：FR-160、SC-030；ADR 0003 单写主权（事务内撤销）。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@DisplayName("T042 权限撤销时效基线测试（FR-160 ≤ 30 秒）")
public class PermissionRevocationSlaTest {

    /** FR-160 撤销时效 SLO：30 秒。 */
    private static final Duration REVOCATION_SLA = Duration.ofSeconds(30);

    /** MySQL 8.4 LTS 容器。 */
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
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.username", MYSQL::getUsername);
        registry.add("spring.datasource.dynamic.datasource.pdpPrimary.password", MYSQL::getPassword);
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

    private final String username = "sla-" + UUID.randomUUID().toString().substring(0, 8);
    private final String email = username + "@pdp.test";

    @BeforeEach
    void seedActiveUser() {
        UserAccount draft = new UserAccount(
                UuidV7Generator.next(), username, "SLA Test", email,
                UserStatus.DRAFT, Instant.now(), Instant.now(), 1);
        accountRepository.save(draft);
        lifecycleService.activate(draft.id());
    }

    @AfterEach
    void cleanup() {
        accountRepository.findByUsername(username).ifPresent(u ->
                accountRepository.updateStatus(u.id(), UserStatus.DEPARTED, u.revision()));
    }

    private UUID userId() {
        return accountRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("种子用户丢失"))
                .id();
    }

    // ============================================================
    // 离职即时失效
    // ============================================================

    @Nested
    @DisplayName("离职即时失效")
    class DepartImmediateRevocation {

        @Test
        @DisplayName("depart 后 5 个会话全部在 30 秒内撤销")
        void departRevokesAllSessionsUnderSla() {
            // Given: ACTIVE 用户已签发 5 个有效会话
            UUID uid = userId();
            for (int i = 0; i < 5; i++) {
                seedSession(uid, "depart-hash-" + i);
            }
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).hasSize(5);

            // When: 调用 depart 并计时
            Instant start = Instant.now();
            lifecycleService.depart(uid, "SLA 测试离职");
            Instant end = Instant.now();

            // Then: 撤销在 30 秒 SLA 内完成；findActiveByUser 立即返回空集
            assertThat(Duration.between(start, end)).isLessThanOrEqualTo(REVOCATION_SLA);
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).isEmpty();
        }

        @Test
        @DisplayName("depart 后会话 revokedAt 时间戳落在调用窗口内")
        void departRevokedAtWithinCallWindow() {
            // Given: ACTIVE 用户已签发 1 个会话
            UUID uid = userId();
            seedSession(uid, "ts-hash");

            // When: 记录调用窗口并 depart
            Instant beforeCall = Instant.now();
            lifecycleService.depart(uid, "时间戳测试");
            Instant afterCall = Instant.now();

            // Then: 该会话的 revokedAt 落在调用窗口内（事务一致性）
            UserSession revoked = sessionRepository.findBySessionTokenHash("ts-hash").orElseThrow();
            assertThat(revoked.revokedAt())
                    .as("depart 后 revokedAt 必须非空")
                    .isNotNull();
            assertThat(revoked.revokedAt()).isBetween(beforeCall, afterCall);
        }
    }

    // ============================================================
    // 管理员强制下线
    // ============================================================

    @Nested
    @DisplayName("管理员强制下线")
    class AdminForceRevoke {

        @Test
        @DisplayName("revokeAllSessions 撤销全部会话在 30 秒内")
        void revokeAllSessionsUnderSla() {
            // Given: ACTIVE 用户已签发 10 个有效会话
            UUID uid = userId();
            for (int i = 0; i < 10; i++) {
                seedSession(uid, "admin-hash-" + i);
            }

            // When: 管理员强制下线
            Instant start = Instant.now();
            int revoked = lifecycleService.revokeAllSessions(uid, "管理员强制下线");
            Instant end = Instant.now();

            // Then: 10 个会话全部撤销，SLA 内完成
            assertThat(revoked).isEqualTo(10);
            assertThat(Duration.between(start, end)).isLessThanOrEqualTo(REVOCATION_SLA);
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).isEmpty();
        }
    }

    // ============================================================
    // 大用户量会话撤销
    // ============================================================

    @Nested
    @DisplayName("大用户量会话撤销")
    class BulkSessionRevocation {

        @Test
        @DisplayName("单用户 100 个会话在 30 秒内全部撤销")
        void hundredSessionsRevokedUnderSla() {
            // Given: ACTIVE 用户已签发 100 个有效会话（模拟多设备登录场景）
            UUID uid = userId();
            for (int i = 0; i < 100; i++) {
                seedSession(uid, "bulk-hash-" + i);
            }
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).hasSize(100);

            // When: depart 触发批量撤销
            Instant start = Instant.now();
            lifecycleService.depart(uid, "批量撤销测试");
            Instant end = Instant.now();

            // Then: 100 个会话全部在 SLA 内撤销（单条 UPDATE 原子完成）
            assertThat(Duration.between(start, end)).isLessThanOrEqualTo(REVOCATION_SLA);
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).isEmpty();
        }

        @Test
        @DisplayName("撤销后立即查询返回空集，无传播延迟")
        void queryAfterRevokeReturnsEmptyImmediately() {
            // Given: ACTIVE 用户已签发 50 个有效会话
            UUID uid = userId();
            List<String> hashes = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                String h = "delay-hash-" + i;
                hashes.add(h);
                seedSession(uid, h);
            }

            // When: depart
            lifecycleService.depart(uid, "传播延迟测试");

            // Then: 立即（无延迟等待）查询应返回空集；每个会话状态为已撤销
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).isEmpty();
            for (String h : hashes) {
                UserSession s = sessionRepository.findBySessionTokenHash(h).orElseThrow();
                assertThat(s.revokedAt())
                        .as("会话 " + h + " 撤销后 revokedAt 必须非空")
                        .isNotNull();
                assertThat(s.revokeReason()).contains("DEPARTED");
            }
        }
    }

    // ============================================================
    // 单会话撤销时效
    // ============================================================

    @Nested
    @DisplayName("单会话撤销")
    class SingleSessionRevocation {

        @Test
        @DisplayName("revokeSession 单条撤销在 5 秒内完成")
        void singleRevokeUnderFiveSeconds() {
            // Given: ACTIVE 用户已签发 1 个有效会话
            UUID uid = userId();
            seedSession(uid, "single-hash");
            UserSession session = sessionRepository.findBySessionTokenHash("single-hash").orElseThrow();

            // When: 撤销单会话
            Instant start = Instant.now();
            boolean ok = lifecycleService.revokeSession(session.id(), "单会话撤销测试");
            Instant end = Instant.now();

            // Then: 撤销成功，时效远小于 SLA（5 秒阈值用于回归监控）
            assertThat(ok).isTrue();
            assertThat(Duration.between(start, end)).isLessThanOrEqualTo(Duration.ofSeconds(5));
            assertThat(sessionRepository.findActiveByUser(uid, Instant.now())).isEmpty();
        }
    }

    // ============================================================
    // 测试辅助
    // ============================================================

    private void seedSession(UUID userId, String tokenHash) {
        UserSession session = new UserSession(
                UuidV7Generator.next(), userId, tokenHash, "refresh-" + tokenHash,
                Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS),
                null, null, "127.0.0.1", 1);
        sessionRepository.save(session);
    }
}
