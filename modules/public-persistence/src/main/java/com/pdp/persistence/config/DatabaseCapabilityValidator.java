package com.pdp.persistence.config;

import com.pdp.persistence.provider.DatabaseCapabilityProfile;
import com.pdp.persistence.provider.DatabaseDeploymentProfile;
import com.pdp.persistence.provider.DatabaseType;
import com.pdp.persistence.provider.ValidationStatus;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 数据库能力校验器。
 *
 * <p>启动时探测实际数据库能力并与认证能力矩阵比对。P1 数据库类型仅允许 MYSQL，
 * 版本必须为认证的 8.4，字符集 utf8mb4，排序规则批准的，时区 UTC，引擎 InnoDB。
 * 不匹配时抛出 {@link IllegalStateException}（对 pdpPrimary 与 workflowEngine 启动失败即退出）。
 */
@Component
public class DatabaseCapabilityValidator {

    private static final String CERTIFIED_MAJOR = "8";
    private static final String CERTIFIED_MINOR = "4";
    private static final String EXPECTED_CHARSET = "utf8mb4";
    private static final String EXPECTED_TIMEZONE = "UTC";
    private static final String EXPECTED_ENGINE = "InnoDB";

    /** 认证基线。 */
    public DatabaseCapabilityProfile certifiedProfile() {
        return DatabaseCapabilityProfile.mysql84Baseline();
    }

    /**
     * 探测并校验数据源能力，返回部署事实。
     * 不匹配项以 {@link ValidationStatus#REJECTED} 标记并抛出异常。
     */
    public DatabaseDeploymentProfile validate(DataSource dataSource) {
        Set<String> violations = new HashSet<>();
        String product = null;
        String version = null;
        String driverVersion = null;
        String charset = null;
        String collation = null;
        String timezone = null;
        String engine = null;
        String isolation = null;

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            product = meta.getDatabaseProductName();
            version = meta.getDatabaseProductVersion();
            driverVersion = meta.getDriverVersion();

            if (!"MySQL".equalsIgnoreCase(product)) {
                violations.add("databaseType 必须为 MySQL，实际 " + product);
            }
            if (!isCertified84(version)) {
                violations.add("databaseVersion 必须为认证的 8.4.x，实际 " + version);
            }

            // 探测会话字符集、排序规则、时区、引擎、隔离级别
            try (Statement stmt = conn.createStatement()) {
                charset = queryString(stmt,
                        "SELECT @@character_set_database AS v");
                collation = queryString(stmt,
                        "SELECT @@collation_database AS v");
                timezone = queryString(stmt,
                        "SELECT @@session.time_zone AS v");
                isolation = queryString(stmt,
                        "SELECT @@transaction_isolation AS v");
                engine = queryString(stmt,
                        "SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() LIMIT 1");
            }

            if (!EXPECTED_CHARSET.equalsIgnoreCase(charset)) {
                violations.add("characterSet 必须为 " + EXPECTED_CHARSET + "，实际 " + charset);
            }
            if (collation == null || !collation.toLowerCase().startsWith("utf8mb4_0900")) {
                violations.add("collation 必须为 utf8mb4_0900 系列确定性排序规则，实际 " + collation);
            }
            if (!EXPECTED_TIMEZONE.equalsIgnoreCase(timezone) && !"+00:00".equals(timezone)) {
                violations.add("timezone 必须为 UTC，实际 " + timezone);
            }
            if (!EXPECTED_ENGINE.equalsIgnoreCase(engine)) {
                violations.add("transactionEngine 必须为 InnoDB，实际 " + engine);
            }

        } catch (Exception e) {
            violations.add("探测失败: " + e.getMessage());
        }

        ValidationStatus status = violations.isEmpty()
                ? ValidationStatus.VALIDATED
                : ValidationStatus.REJECTED;

        DatabaseDeploymentProfile profile = new DatabaseDeploymentProfile(
                DatabaseType.MYSQL,
                version,
                driverVersion,
                null,
                charset,
                collation,
                timezone,
                engine,
                isolation,
                Set.of(),
                status,
                Instant.now());

        if (status == ValidationStatus.REJECTED) {
            throw new IllegalStateException(
                    "数据库能力校验失败，启动中止（pdpPrimary/workflowEngine 必须启动失败即退出）: "
                            + String.join("; ", violations));
        }
        return profile;
    }

    private static boolean isCertified84(String version) {
        if (version == null) {
            return false;
        }
        String[] parts = version.split("\\.");
        return parts.length >= 2
                && CERTIFIED_MAJOR.equals(parts[0])
                && CERTIFIED_MINOR.equals(parts[1]);
    }

    private static String queryString(Statement stmt, String sql) {
        try (ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getString("v") : null;
        } catch (Exception e) {
            return null;
        }
    }
}
