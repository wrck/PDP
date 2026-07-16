package com.pdp.workflow;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.pdp.workflow.infrastructure.flowable.FlowableSchemaManifest;
import java.sql.Connection;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.mysql.MySQLContainer;

class FlowableSchemaMySqlMatrixTest {

    @Test
    void initializesEmptyMySqlSchemaFromVersionedManifest() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "需要可用的 Docker 服务");
        try (MySQLContainer mysql = new MySQLContainer("mysql:8.4")
                .withDatabaseName("pdp_workflow")) {
            mysql.start();
            FlowableSchemaManifest manifest = FlowableSchemaManifest.load();
            try (Connection connection = mysql.createConnection("")) {
                for (String script : manifest.createScripts()) {
                    ScriptUtils.executeSqlScript(connection, new ClassPathResource(script));
                }
                assertThat(tableExists(connection, "ACT_GE_PROPERTY")).isTrue();
                assertThat(tableExists(connection, "ACT_RE_PROCDEF")).isTrue();
                assertThat(tableExists(connection, "ACT_HI_PROCINST")).isTrue();
            }
        }
    }

    @Test
    void plansPreviousVersionUpgradeAndRejectsUnsupportedVersion() {
        FlowableSchemaManifest manifest = FlowableSchemaManifest.load();
        assertThat(manifest.targetVersion()).isEqualTo("8.0.0");
        assertThat(manifest.upgradeScriptsFrom("7.2.2"))
                .containsExactly(
                        "org/flowable/db/upgrade/flowable.mysql.upgradestep.7202.to.8000.engine.sql",
                        "org/flowable/db/upgrade/flowable.mysql.upgradestep.7202.to.8000.history.sql");
        assertThatThrownBy(() -> manifest.upgradeScriptsFrom("6.8.0"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
    }

    private static boolean tableExists(Connection connection, String name) throws Exception {
        try (var tables = connection.getMetaData()
                .getTables(connection.getCatalog(), null, name, new String[] {"TABLE"})) {
            return tables.next();
        }
    }
}
