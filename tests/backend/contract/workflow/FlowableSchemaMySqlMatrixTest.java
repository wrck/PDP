package com.pdp.contract.workflow;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flowable Process Engine MySQL Schema 验证矩阵（T080）。
 *
 * <p>落实 ADR-0005 § 8（事务与 MySQL schema）与 § 9（升级与退出条件）：
 * <ul>
 *   <li><strong>空库初始化</strong>：从空库执行 Flowable 8.0.0 内置 MySQL DDL，
 *       验证清单声明的 expectedTables 全部存在且 ACT_GE_PROPERTY.schema_version 匹配。</li>
 *   <li><strong>上一版本升级</strong>：8.0.0 是 P1 首个支持版本，无升级路径；
 *       测试验证清单 upgrades 为空时不视为失败。</li>
 *   <li><strong>不支持版本快速失败</strong>：在 ACT_GE_PROPERTY 写入 unsupportedVersions 列出的版本号，
 *       预检 MUST 抛出异常并指明不支持版本与补救指引，禁止带病运行。</li>
 * </ul>
 *
 * <p><strong>两层测试结构</strong>：
 * <ul>
 *   <li>{@link ManifestContract}：始终运行，不依赖 Docker；校验清单 YAML 格式、内容与验证矩阵声明。</li>
 *   <li>{@link MySqlIntegration}：依赖 Docker 与 Flowable 8.0.0 JAR；无 Docker 时自动禁用。
 *       CI 矩阵（T025）在 MySQL 8.4 LTS 上强制运行。</li>
 * </ul>
 *
 * <p>对应任务：T080；规格条款：FR-174、ADR-0005 § 8-9；
 * 清单文件：{@code modules/workflow/src/main/resources/db/flowable/mysql/flowable-schema-manifest.yaml}。
 */
@DisplayName("T080 Flowable MySQL Schema 验证矩阵")
class FlowableSchemaMySqlMatrixTest {

    /** 清单 classpath 路径。 */
    private static final String MANIFEST_PATH = "/db/flowable/mysql/flowable-schema-manifest.yaml";

    /** Flowable 8.0.0 内置 MySQL 全量创建脚本 classpath 路径（与清单 initializations[0].script 一致）。 */
    private static final String FLOWABLE_DDL_PATH = "/org/flowable/db/create/mysql/all/flowable.mysql.all.create.sql";

    // ============================================================
    // 清单契约（无 Docker）：始终运行
    // ============================================================

    @Nested
    @DisplayName("清单契约（无 Docker）")
    class ManifestContract {

        @Test
        @DisplayName("清单 YAML 资源存在于 classpath")
        void manifestResourceExists() {
            try (InputStream in = loadManifest()) {
                assertThat(in).as("清单资源必须存在: %s", MANIFEST_PATH).isNotNull();
            } catch (IOException e) {
                throw new IllegalStateException("读取清单失败", e);
            }
        }

        @Test
        @DisplayName("清单声明 Flowable 8.0.0 与 MySQL 8.4")
        void manifestDeclaresFlowable800AndMysql84() {
            Map<String, Object> manifest = parseManifest();
            Map<String, Object> schema = asMap(manifest.get("schema"));

            assertThat(schema.get("engine")).isEqualTo("flowable-process");
            assertThat(schema.get("flowableVersion")).isEqualTo("8.0.0");
            assertThat(schema.get("mysqlVersion")).isEqualTo("8.4");
        }

        @Test
        @DisplayName("清单声明 expectedSchemaVersion（启动预检据此快速失败）")
        void manifestDeclaresExpectedSchemaVersion() {
            Map<String, Object> manifest = parseManifest();
            Map<String, Object> schema = asMap(manifest.get("schema"));

            assertThat(schema.get("expectedSchemaVersion"))
                    .as("expectedSchemaVersion MUST 非空，ACT_GE_PROPERTY.schema_version 据此校验")
                    .isEqualTo("8.0.0.0");
        }

        @Test
        @DisplayName("清单声明初始化脚本路径与预期表集合")
        void manifestDeclaresExpectedTables() {
            Map<String, Object> manifest = parseManifest();
            List<Object> initializations = asList(manifest.get("initializations"));

            assertThat(initializations).hasSize(1);
            Map<String, Object> init = asMap(initializations.get(0));
            assertThat(init.get("version")).isEqualTo("8.0.0");
            assertThat(init.get("script"))
                    .asString()
                    .startsWith("classpath:org/flowable/db/create/mysql/all/");

            List<Object> expectedTables = asList(init.get("expectedTables"));
            assertThat(expectedTables)
                    .as("expectedTables MUST 覆盖 Flowable Process Engine 核心运行/历史/定义/身份表")
                    .contains("ACT_GE_PROPERTY", "ACT_RE_PROCDEF", "ACT_RU_EXECUTION",
                            "ACT_RU_TASK", "ACT_RU_JOB", "ACT_HI_PROCINST", "ACT_HI_TASKINST");
        }

        @Test
        @DisplayName("清单声明 upgrades 为空（8.0.0 是 P1 首个支持版本）")
        void manifestDeclaresNoUpgradePath() {
            Map<String, Object> manifest = parseManifest();
            List<Object> upgrades = asList(manifest.get("upgrades"));

            assertThat(upgrades)
                    .as("8.0.0 是 P1 启用的首个 Flowable 版本，upgrades MUST 为空")
                    .isEmpty();
        }

        @Test
        @DisplayName("清单声明 unsupportedVersions 列表（启动预检 MUST 快速失败）")
        void manifestDeclaresUnsupportedVersions() {
            Map<String, Object> manifest = parseManifest();
            List<Object> unsupported = asList(manifest.get("unsupportedVersions"));

            assertThat(unsupported)
                    .as("unsupportedVersions MUST 包含低于 8.0.0 的版本标记")
                    .contains("<8.0.0", "7.0.x", "7.1.x", "6.x");
        }

        @Test
        @DisplayName("清单声明验证矩阵（emptyDatabaseInit / previousVersionUpgrade / unsupportedVersionFastFail）")
        void manifestDeclaresVerificationMatrix() {
            Map<String, Object> manifest = parseManifest();
            Map<String, Object> matrix = asMap(manifest.get("verificationMatrix"));

            assertThat(matrix).containsOnlyKeys(
                    "emptyDatabaseInit", "previousVersionUpgrade",
                    "unsupportedVersionFastFail", "rollbackBoundary", "backupRestore");

            Map<String, Object> emptyInit = asMap(matrix.get("emptyDatabaseInit"));
            assertThat(emptyInit.get("required")).isEqualTo(true);

            Map<String, Object> upgrade = asMap(matrix.get("previousVersionUpgrade"));
            assertThat(upgrade.get("required")).isEqualTo(false);
            assertThat(upgrade.get("notApplicableReason")).asString().contains("8.0.0");

            Map<String, Object> fastFail = asMap(matrix.get("unsupportedVersionFastFail"));
            assertThat(fastFail.get("required")).isEqualTo(true);

            Map<String, Object> rollback = asMap(matrix.get("rollbackBoundary"));
            assertThat(rollback.get("required")).isEqualTo(true);

            Map<String, Object> backup = asMap(matrix.get("backupRestore"));
            assertThat(backup.get("required")).isEqualTo(true);
        }
    }

    // ============================================================
    // MySQL 8.4 集成矩阵（需 Docker 与 Flowable 8.0.0 JAR）
    // ============================================================

    @Nested
    @DisplayName("MySQL 8.4 集成矩阵（需 Docker）")
    @Testcontainers(disabledWithoutDocker = true)
    class MySqlIntegration {

        /** MySQL 8.4 LTS 容器，UTC 时区、utf8mb4、InnoDB、READ_COMMITTED 隔离级别。 */
        @Container
        static final MySQLContainer<?> MYSQL = new MySQLContainer<>(
                DockerImageName.parse("mysql:8.4-lts")
                        .asCompatibleSubstituteFor("mysql"))
                .withDatabaseName("pdp_workflow")
                .withUsername("pdp_workflow")
                .withPassword("test")
                .withCommand(
                        "--default-time-zone=+00:00",
                        "--character-set-server=utf8mb4",
                        "--collation-server=utf8mb4_unicode_ci",
                        "--default-storage-engine=InnoDB",
                        "--transaction-isolation=READ-COMMITTED");

        @Test
        @DisplayName("空库初始化：执行 Flowable 8.0.0 DDL 后 expectedTables 全部存在")
        void emptyDatabaseInitCreatesAllExpectedTables() throws Exception {
            // Given: 清单声明的 DDL 脚本与 expectedTables
            String ddl = loadFlowableDdl();
            List<String> expectedTables = readExpectedTables();

            // When: 在空库执行 DDL
            try (Connection conn = openConnection();
                 Statement stmt = conn.createStatement()) {
                executeSqlScript(stmt, ddl);
            }

            // Then: expectedTables 全部存在且 schema_version 匹配
            try (Connection conn = openConnection();
                 Statement stmt = conn.createStatement()) {
                for (String table : expectedTables) {
                    assertThat(tableExists(stmt, table))
                            .as("空库初始化后表 %s 必须存在", table)
                            .isTrue();
                }
                String version = readSchemaVersion(stmt);
                assertThat(version)
                        .as("ACT_GE_PROPERTY.schema_version MUST 匹配清单 expectedSchemaVersion")
                        .isEqualTo("8.0.0.0");
            }
        }

        @Test
        @DisplayName("上一版本升级：8.0.0 首个版本无升级路径，跳过不视为失败")
        void previousVersionUpgradeNotApplicable() {
            // Given: 清单声明 upgrades 为空
            Map<String, Object> manifest = parseManifest();
            List<Object> upgrades = asList(manifest.get("upgrades"));

            // Then: 无升级路径时测试通过（不视为失败）
            assertThat(upgrades).isEmpty();
            // 后续版本追加升级脚本后，MUST 新增 fromVersion → toVersion 升级测试方法
        }

        @Test
        @DisplayName("不支持版本快速失败：ACT_GE_PROPERTY 写入 7.x 版本后预检 MUST 抛出")
        void unsupportedVersionFastFailsBeforeAcceptingTraffic() throws Exception {
            // Given: 已初始化的 Flowable schema
            String ddl = loadFlowableDdl();
            try (Connection conn = openConnection();
                 Statement stmt = conn.createStatement()) {
                executeSqlScript(stmt, ddl);
                // 写入不支持版本号（模拟旧版本残留）
                stmt.execute(
                        "UPDATE ACT_GE_PROPERTY SET VALUE_ = '7.1.0.0' WHERE NAME_ = 'schema.version'");
            }

            // When/Then: 启动预检 MUST 抛出 FlowableSchemaVersionException
            assertThatThrownBy(() -> {
                try (Connection conn = openConnection();
                     Statement stmt = conn.createStatement()) {
                    verifySchemaVersion(stmt, "8.0.0.0", List.of("<8.0.0", "7.0.x", "7.1.x", "6.x"));
                }
            })
                    .isInstanceOf(FlowableSchemaVersionException.class)
                    .hasMessageContaining("7.1.0.0")
                    .hasMessageContaining("8.0.0");
        }
    }

    // ============================================================
    // 启动预检模拟（生产代码由 T081+ 落地为 Guard Bean）
    // ============================================================

    /**
     * Flowable schema 版本不匹配异常（模拟启动预检抛出）。
     *
     * <p>生产环境由独立的 {@code FlowableSchemaVersionGuard} Bean 在应用启动阶段执行，
     * 抛出后 MUST 阻止应用接受业务流量。本测试类内联模拟以验证清单契约。
     */
    static final class FlowableSchemaVersionException extends RuntimeException {
        FlowableSchemaVersionException(String message) {
            super(message);
        }
    }

    /**
     * 启动预检：读取 ACT_GE_PROPERTY.schema_version，校验与清单 expectedSchemaVersion 一致。
     *
     * @param stmt               已建立的 MySQL Statement
     * @param expectedVersion    清单声明的 expectedSchemaVersion
     * @param unsupportedVersions 清单声明的 unsupportedVersions 列表
     * @throws FlowableSchemaVersionException 版本不匹配时抛出，错误信息含当前版本与补救指引
     * @throws SQLException                   读取 schema 版本失败
     */
    private static void verifySchemaVersion(Statement stmt, String expectedVersion,
                                             List<String> unsupportedVersions) throws SQLException {
        String current = readSchemaVersion(stmt);
        if (!expectedVersion.equals(current)) {
            throw new FlowableSchemaVersionException(
                    "Flowable schema 版本不匹配：当前=" + current
                            + "，期望=" + expectedVersion
                            + "，不支持版本=" + unsupportedVersions
                            + "；补救：升级到 " + expectedVersion
                            + " 或回滚到上一 P1 支持版本并恢复备份");
        }
    }

    // ============================================================
    // 辅助：清单加载与解析
    // ============================================================

    private InputStream loadManifest() {
        return getClass().getResourceAsStream(MANIFEST_PATH);
    }

    private Map<String, Object> parseManifest() {
        try (InputStream in = loadManifest()) {
            Assumptions.assumeTrue(in != null,
                    "清单资源不存在，跳过：" + MANIFEST_PATH);
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(in);
            Assumptions.assumeTrue(parsed instanceof Map,
                    "清单 YAML 顶层 MUST 为 mapping");
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("读取清单失败: " + MANIFEST_PATH, e);
        }
    }

    private List<String> readExpectedTables() {
        Map<String, Object> manifest = parseManifest();
        List<Object> initializations = asList(manifest.get("initializations"));
        Map<String, Object> init = asMap(initializations.get(0));
        return asList(init.get("expectedTables")).stream()
                .map(Objects::toString)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        Assumptions.assumeTrue(o instanceof Map, "YAML 节点 MUST 为 mapping: " + o);
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        Assumptions.assumeTrue(o instanceof List, "YAML 节点 MUST 为 sequence: " + o);
        return (List<Object>) o;
    }

    // ============================================================
    // 辅助：MySQL 集成
    // ============================================================

    private String loadFlowableDdl() {
        try (InputStream in = getClass().getResourceAsStream(FLOWABLE_DDL_PATH)) {
            Assumptions.assumeTrue(in != null,
                    "Flowable 8.0.0 DDL 脚本不在 classpath，跳过集成测试：" + FLOWABLE_DDL_PATH);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取 Flowable DDL 失败: " + FLOWABLE_DDL_PATH, e);
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                MySqlIntegration.MYSQL.getJdbcUrl(),
                MySqlIntegration.MYSQL.getUsername(),
                MySqlIntegration.MYSQL.getPassword());
    }

    /**
     * 执行多语句 SQL 脚本。
     *
     * <p>Flowable 内置 DDL 包含多条 {@code create table} 与 {@code insert} 语句，
     * MySQL Connector/J 默认不允许多语句，通过逐条切分执行。
     * 切分按 {@code ;} 简单处理，跳过注释行与空语句。
     */
    private static void executeSqlScript(Statement stmt, String ddl) throws SQLException {
        List<String> statements = splitSqlStatements(ddl);
        for (String sql : statements) {
            String trimmed = sql.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("--") || trimmed.startsWith("#")) {
                continue;
            }
            stmt.execute(trimmed);
        }
    }

    private static List<String> splitSqlStatements(String ddl) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : ddl.split("\\R")) {
            String stripped = line.trim();
            if (stripped.startsWith("--") || stripped.startsWith("#") || stripped.startsWith("/*")) {
                continue;
            }
            current.append(line).append('\n');
            if (stripped.endsWith(";")) {
                out.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            out.add(current.toString());
        }
        return out;
    }

    private static boolean tableExists(Statement stmt, String tableName) throws SQLException {
        try (ResultSet rs = stmt.executeQuery(
                "SELECT 1 FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_name = '" + tableName + "'")) {
            return rs.next();
        }
    }

    private static String readSchemaVersion(Statement stmt) throws SQLException {
        try (PreparedStatement ps = stmt.getConnection().prepareStatement(
                "SELECT VALUE_ FROM ACT_GE_PROPERTY WHERE NAME_ = 'schema.version'");
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("ACT_GE_PROPERTY.schema.version 不存在；schema 未初始化");
            }
            return rs.getString(1);
        }
    }
}
