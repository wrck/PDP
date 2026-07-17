package com.pdp.contract.domainconfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.domainconfig.application.DomainPackageCompositionService;
import com.pdp.domainconfig.application.DomainPackageMigrationService;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationJob;
import com.pdp.domainconfig.application.DomainPackageMigrationService.MigrationPreview;
import com.pdp.domainconfig.domain.metamodel.DomainPackageManifest;
import com.pdp.domainconfig.domain.metamodel.FieldDefinition;
import com.pdp.domainconfig.domain.metamodel.ObjectDefinition;
import com.pdp.domainconfig.domain.packageversion.DomainPackage;
import com.pdp.domainconfig.domain.packageversion.DomainPackageVersion;
import com.pdp.domainconfig.domain.packageversion.PackageLayer;
import com.pdp.mysql.domainconfig.CoreFieldCatalogMapper;
import com.pdp.mysql.domainconfig.DomainPackageMapper;
import com.pdp.mysql.domainconfig.DomainPackageMigrationMapper;
import com.pdp.mysql.domainconfig.DomainPackageSnapshotMapper;
import com.pdp.mysql.domainconfig.DomainPackageVersionMapper;
import com.pdp.mysql.domainconfig.MysqlCoreFieldCatalog;
import com.pdp.mysql.domainconfig.MysqlDomainPackageMigrationRepository;
import com.pdp.mysql.domainconfig.MysqlDomainPackageRepository;
import com.pdp.mysql.domainconfig.MysqlDomainPackageSnapshotRepository;
import com.pdp.mysql.domainconfig.MysqlDomainPackageVersionRepository;
import com.pdp.mysql.typehandler.UuidBinaryTypeHandler;
import com.pdp.shared.concurrency.Revision;
import com.pdp.shared.operation.HighRiskOperationType;
import com.pdp.shared.operation.OperationImpactPreview;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

class DomainPackageDatabaseContractTest {

  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void publicSchemaAndMysqlMappingsDeclareJsonUniquenessAndKeysetContracts()
      throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:domain-package-schema;DB_CLOSE_DELAY=-1");
    applyChangelog(dataSource, "db/changelog/db.changelog-master.xml");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN (
                  'PDP_CORE_FIELD_CATALOG', 'PDP_DOMAIN_PACKAGE',
                  'PDP_DOMAIN_PACKAGE_VERSION', 'PDP_OBJECT_DEFINITION',
                  'PDP_FIELD_DEFINITION', 'PDP_RELATION_DEFINITION',
                  'PDP_PAGE_DEFINITION', 'PDP_BEHAVIOR_DEFINITION',
                  'PDP_PACKAGE_OVERRIDE', 'PDP_DOMAIN_PACKAGE_SNAPSHOT',
                  'PDP_DOMAIN_PACKAGE_MIGRATION'
                )
                """,
                Integer.class))
        .isEqualTo(11);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_NAME IN (
                  'UK_DOMAIN_PACKAGE_WORKSPACE_KEY', 'UK_PACKAGE_SEMANTIC_VERSION',
                  'UK_PACKAGE_CONTENT_HASH', 'UK_OBJECT_DEFINITION_VERSION_KEY',
                  'UK_FIELD_DEFINITION_OBJECT_KEY', 'UK_BEHAVIOR_VERSION_TYPE_KEY'
                )
                """,
                Integer.class))
        .isEqualTo(6);

    String packageMapper = resource("mapper/domainconfig/DomainPackageMapper.xml");
    String versionMapper = resource("mapper/domainconfig/DomainPackageVersionMapper.xml");
    String snapshotMapper = resource("mapper/domainconfig/DomainPackageSnapshotMapper.xml");
    String migrationMapper = resource("mapper/domainconfig/DomainPackageMigrationMapper.xml");
    String mysqlTypes = resource("db/changelog/mysql/004-domainconfig-types.xml");

    assertThat(packageMapper + versionMapper)
        .contains(
            "JsonDocumentTypeHandler",
            "UuidBinaryTypeHandler",
            "AND revision = #{revision} - 1",
            "findPageAfter",
            "LIMIT #{limit}")
        .doesNotContain("OFFSET", "SELECT *");
    assertThat(snapshotMapper)
        .contains("package_version_id", "#{packageVersionId")
        .doesNotContain("#{id,typeHandler=com.pdp.mysql.typehandler.UuidBinaryTypeHandler},\n      #{id");
    assertThat(migrationMapper)
        .contains("preview_id", "status = 'PREVIEW'", "status != 'PREVIEW'")
        .doesNotContain("findLatestPreview");
    assertThat(mysqlTypes)
        .contains("MODIFY manifest JSON NOT NULL", "MODIFY snapshot JSON NOT NULL")
        .contains("MODIFY package_version_id BINARY(16) NOT NULL");
  }

  @Test
  void mysqlEquivalentRuntimeRoundTripsJsonInheritanceAssociationsAndPages()
      throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:domain-package-runtime;MODE=MySQL;DB_CLOSE_DELAY=-1");
    createRuntimeSchema(dataSource);
    exerciseRuntimeContract(dataSource);
  }

  @Test
  void mysql84EnforcesTheSameDomainPackageContract() throws Exception {
    Assumptions.assumeTrue(
        DockerClientFactory.instance().isDockerAvailable(), "Docker is required for MySQL 8.4");

    try (MySQLContainer<?> mysql =
        new MySQLContainer<>("mysql:8.4.6")
            .withDatabaseName("pdp")
            .withUsername("pdp")
            .withPassword("pdp")) {
      mysql.start();
      try (HikariDataSource dataSource = dataSource(mysql)) {
        applyChangelog(dataSource, "db/changelog/db.changelog-mysql.xml");
        exerciseRuntimeContract(dataSource);
      }
    }
  }

  private static void exerciseRuntimeContract(DataSource dataSource) throws Exception {
    seedCoreField(dataSource);
    SqlSessionFactory factory = sessionFactory(dataSource);
    Fixture fixture = persistPackagesVersionsAndSnapshot(factory);

    verifyJsonInheritanceCoreConflictAndPages(factory, fixture);
    verifyUniqueness(factory, fixture);
    verifyMigrationAssociations(factory, fixture);

    byte[] linkedVersion =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT package_version_id FROM pdp_domain_package_snapshot WHERE id = ?",
                byte[].class,
                UuidBinaryTypeHandler.toBytes(fixture.snapshotId()));
    assertThat(UuidBinaryTypeHandler.fromBytes(linkedVersion))
        .isEqualTo(fixture.customerVersion().id())
        .isNotEqualTo(fixture.snapshotId());
  }

  private static Fixture persistPackagesVersionsAndSnapshot(SqlSessionFactory factory) {
    UUID workspaceId = uuid(1);
    DomainPackage platform =
        new DomainPackage(
            uuid(10),
            workspaceId,
            "platform.standard",
            "平台标准包",
            PackageLayer.PLATFORM_STANDARD,
            null,
            DomainPackage.Status.ACTIVE,
            new Revision(0));
    DomainPackage industry =
        new DomainPackage(
            uuid(11),
            workspaceId,
            "industry.network",
            "网络行业包",
            PackageLayer.INDUSTRY,
            platform.id(),
            DomainPackage.Status.ACTIVE,
            new Revision(0));
    DomainPackage customer =
        new DomainPackage(
            uuid(12),
            workspaceId,
            "workspace.customer",
            "客户定制包",
            PackageLayer.WORKSPACE_CUSTOMER,
            industry.id(),
            DomainPackage.Status.ACTIVE,
            new Revision(0));

    DomainPackageManifest platformManifest =
        manifest("platform.standard", PackageLayer.PLATFORM_STANDARD, null, "project");
    DomainPackageManifest industryManifest =
        manifest("industry.network", PackageLayer.INDUSTRY, "platform.standard", "device");
    DomainPackageManifest customerManifest =
        manifest(
            "workspace.customer", PackageLayer.WORKSPACE_CUSTOMER, "industry.network", "site");
    DomainPackageVersion v100 =
        version(uuid(20), platform.id(), "1.0.0", platformManifest, "hash-100", NOW);
    DomainPackageVersion v110 =
        version(uuid(21), platform.id(), "1.1.0", platformManifest, "hash-110", NOW.plusSeconds(1));
    DomainPackageVersion customerVersion =
        version(
            uuid(22), customer.id(), "2.0.0", customerManifest, "hash-customer", NOW.plusSeconds(2));

    UUID snapshotId;
    try (var session = factory.openSession(false)) {
      var packages = new MysqlDomainPackageRepository(session.getMapper(DomainPackageMapper.class));
      packages.save(platform);
      packages.save(industry);
      packages.save(customer);
      var versions =
          new MysqlDomainPackageVersionRepository(
              session.getMapper(DomainPackageVersionMapper.class));
      versions.save(v100);
      versions.save(v110);
      versions.save(customerVersion);
      var composition =
          new DomainPackageCompositionService()
              .compose(
                  customerVersion.id(), platformManifest, industryManifest, customerManifest);
      new MysqlDomainPackageSnapshotRepository(
              session.getMapper(DomainPackageSnapshotMapper.class))
          .save(composition.snapshot());
      snapshotId = composition.snapshot().id();
      session.commit();
    }
    return new Fixture(
        workspaceId,
        platform,
        industry,
        customer,
        v100,
        v110,
        customerVersion,
        snapshotId);
  }

  private static void verifyJsonInheritanceCoreConflictAndPages(
      SqlSessionFactory factory, Fixture fixture) {
    try (var session = factory.openSession(true)) {
      DomainPackageMapper packageMapper = session.getMapper(DomainPackageMapper.class);
      DomainPackageVersionMapper versionMapper =
          session.getMapper(DomainPackageVersionMapper.class);
      var packages = new MysqlDomainPackageRepository(packageMapper);
      var versions = new MysqlDomainPackageVersionRepository(versionMapper);

      assertThat(packages.findByWorkspace(fixture.workspaceId()))
          .extracting(DomainPackage::id)
          .containsExactly(fixture.industry().id(), fixture.platform().id(), fixture.customer().id());
      assertThat(packages.findById(fixture.industry().id()))
          .hasValueSatisfying(
              value -> assertThat(value.parentPackageId()).isEqualTo(fixture.platform().id()));
      assertThat(packages.findById(fixture.customer().id()))
          .hasValueSatisfying(
              value -> assertThat(value.parentPackageId()).isEqualTo(fixture.industry().id()));
      assertThat(
              versions.findByPackageAndSemanticVersion(
                  fixture.platform().id(), fixture.v100().semanticVersion()))
          .hasValueSatisfying(
              value ->
                  assertThat(value.manifest().objects().getFirst().stableKey())
                      .isEqualTo("project"));

      var candidate =
          new FieldDefinition(
              "custom.customer.name", "客户名称", "TEXT", "custom_customer_name", false, false);
      assertThat(
              new MysqlCoreFieldCatalog(session.getMapper(CoreFieldCatalogMapper.class))
                  .load()
                  .semanticConflict(candidate))
          .hasValueSatisfying(
              core -> assertThat(core.stableKey()).isEqualTo("project.customer_name"));

      var firstPackagePage =
          packageMapper.findPageAfter(fixture.workspaceId(), null, null, 1);
      assertThat(firstPackagePage).hasSize(1);
      var packageCursor = firstPackagePage.getFirst();
      assertThat(
              packageMapper.findPageAfter(
                  fixture.workspaceId(), packageCursor.stableKey(), packageCursor.id(), 2))
          .hasSize(2)
          .extracting(row -> row.id())
          .doesNotContain(packageCursor.id());

      var firstVersionPage = versionMapper.findPageAfter(fixture.platform().id(), null, null, 1);
      assertThat(firstVersionPage).hasSize(1);
      var versionCursor = firstVersionPage.getFirst();
      assertThat(
              versionMapper.findPageAfter(
                  fixture.platform().id(), versionCursor.createdAt(), versionCursor.id(), 1))
          .extracting(row -> row.id())
          .containsExactly(fixture.v110().id());
    }
  }

  private static void verifyUniqueness(SqlSessionFactory factory, Fixture fixture) {
    try (var session = factory.openSession(false)) {
      var repository = new MysqlDomainPackageRepository(session.getMapper(DomainPackageMapper.class));
      DomainPackage duplicate =
          new DomainPackage(
              uuid(30),
              fixture.workspaceId(),
              fixture.platform().stableKey(),
              "重复领域包",
              PackageLayer.PLATFORM_STANDARD,
              null,
              DomainPackage.Status.DRAFT,
              new Revision(0));
      assertThatThrownBy(() -> repository.save(duplicate))
          .isInstanceOf(PersistenceException.class);
      session.rollback();
    }

    try (var session = factory.openSession(false)) {
      var repository =
          new MysqlDomainPackageVersionRepository(
              session.getMapper(DomainPackageVersionMapper.class));
      DomainPackageVersion duplicateSemantic =
          version(
              uuid(31),
              fixture.platform().id(),
              fixture.v100().semanticVersion(),
              fixture.v100().manifest(),
              "hash-other",
              NOW.plusSeconds(3));
      assertThatThrownBy(() -> repository.save(duplicateSemantic))
          .isInstanceOf(PersistenceException.class);
      session.rollback();
    }

    try (var session = factory.openSession(false)) {
      var repository =
          new MysqlDomainPackageVersionRepository(
              session.getMapper(DomainPackageVersionMapper.class));
      DomainPackageVersion duplicateHash =
          version(
              uuid(32),
              fixture.platform().id(),
              "1.2.0",
              fixture.v100().manifest(),
              fixture.v100().contentHash(),
              NOW.plusSeconds(4));
      assertThatThrownBy(() -> repository.save(duplicateHash))
          .isInstanceOf(PersistenceException.class);
      session.rollback();
    }
  }

  private static void verifyMigrationAssociations(SqlSessionFactory factory, Fixture fixture) {
    UUID previewId = uuid(40);
    MigrationPreview preview =
        new MigrationPreview(
            previewId,
            fixture.v100().id(),
            fixture.v110().id(),
            25,
            1,
            List.of("需要校验自定义字段"),
            true,
            "command-digest",
            "revision-digest",
            new OperationImpactPreview(
                previewId,
                HighRiskOperationType.DOMAIN_PACKAGE_MIGRATION,
                Map.of("instances", 25L),
                List.of("需要维护窗口"),
                "首次写入目标版本后",
                "恢复源版本快照",
                "confirmation-token",
                NOW.plusSeconds(900)));
    MigrationJob job =
        new MigrationJob(
            uuid(41),
            preview.id(),
            fixture.v100().id(),
            fixture.v110().id(),
            DomainPackageMigrationService.Status.PLANNED,
            100,
            0,
            0,
            List.of(),
            new Revision(0),
            NOW);

    try (var session = factory.openSession(false)) {
      var repository =
          new MysqlDomainPackageMigrationRepository(
              session.getMapper(DomainPackageMigrationMapper.class));
      assertThat(repository.savePreview(preview)).isEqualTo(preview);
      assertThat(repository.saveJob(job).previewId()).isEqualTo(preview.id());
      session.commit();
    }

    try (var session = factory.openSession(false)) {
      var repository =
          new MysqlDomainPackageMigrationRepository(
              session.getMapper(DomainPackageMigrationMapper.class));
      MigrationJob running =
          new MigrationJob(
              job.id(),
              job.previewId(),
              job.sourceVersionId(),
              job.targetVersionId(),
              DomainPackageMigrationService.Status.RUNNING,
              job.batchSize(),
              20,
              1,
              List.of(uuid(42)),
              job.revision().next(),
              job.createdAt());
      assertThat(repository.saveJob(running))
          .satisfies(
              saved -> {
                assertThat(saved.previewId()).isEqualTo(preview.id());
                assertThat(saved.failedInstances()).containsExactly(uuid(42));
              });
      session.commit();
    }

    try (var session = factory.openSession(false)) {
      var repository =
          new MysqlDomainPackageMigrationRepository(
              session.getMapper(DomainPackageMigrationMapper.class));
      MigrationJob orphan =
          new MigrationJob(
              uuid(43),
              uuid(999),
              fixture.v100().id(),
              fixture.v110().id(),
              DomainPackageMigrationService.Status.PLANNED,
              100,
              0,
              0,
              List.of(),
              new Revision(0),
              NOW);
      assertThatThrownBy(() -> repository.saveJob(orphan))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining(orphan.previewId().toString());
      session.rollback();
    }
  }

  private static DomainPackageVersion version(
      UUID id,
      UUID packageId,
      String semanticVersion,
      DomainPackageManifest manifest,
      String hash,
      Instant createdAt) {
    return new DomainPackageVersion(
        id,
        packageId,
        semanticVersion,
        manifest,
        hash,
        DomainPackageVersion.Status.DRAFT,
        false,
        uuid(500),
        null,
        null,
        new Revision(0),
        createdAt);
  }

  private static DomainPackageManifest manifest(
      String stableKey, PackageLayer layer, String parent, String objectKey) {
    return new DomainPackageManifest(
        stableKey,
        layer,
        parent,
        List.of(
            new ObjectDefinition(
                objectKey,
                ObjectDefinition.Kind.NEW_OBJECT,
                null,
                List.of(
                    new FieldDefinition(
                        objectKey + ".name",
                        objectKey + "名称",
                        "TEXT",
                        objectKey + "_name",
                        true,
                        false)),
                List.of(),
                List.of(),
                List.of(),
                List.of())),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static void seedCoreField(DataSource dataSource) throws Exception {
    try (Connection connection = dataSource.getConnection();
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO pdp_core_field_catalog (
                  id, stable_key, object_type, semantic_name, data_type,
                  data_source, aliases, extensible, revision
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
      statement.setBytes(1, UuidBinaryTypeHandler.toBytes(uuid(100)));
      statement.setString(2, "project.customer_name");
      statement.setString(3, "PROJECT");
      statement.setString(4, "客户");
      statement.setString(5, "TEXT");
      statement.setString(6, "project.customer_id");
      statement.setString(7, "[\"客户名称\",\"客户名\"]");
      statement.setBoolean(8, false);
      statement.setLong(9, 0);
      statement.executeUpdate();
      connection.commit();
    }
  }

  private static void createRuntimeSchema(DataSource dataSource) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute(
        """
        CREATE TABLE pdp_core_field_catalog (
          id BINARY(16) PRIMARY KEY, stable_key VARCHAR(100) NOT NULL UNIQUE,
          object_type VARCHAR(100) NOT NULL, semantic_name VARCHAR(200) NOT NULL,
          data_type VARCHAR(30) NOT NULL, data_source VARCHAR(200) NOT NULL UNIQUE,
          aliases CLOB NOT NULL, extensible BOOLEAN NOT NULL, revision BIGINT NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE TABLE pdp_domain_package (
          id BINARY(16) PRIMARY KEY, workspace_id BINARY(16) NOT NULL,
          stable_key VARCHAR(100) NOT NULL, name VARCHAR(100) NOT NULL,
          layer VARCHAR(30) NOT NULL, parent_package_id BINARY(16),
          status VARCHAR(20) NOT NULL, revision BIGINT NOT NULL,
          CONSTRAINT uk_domain_package_workspace_key UNIQUE (workspace_id, stable_key)
        )
        """);
    jdbc.execute(
        """
        CREATE TABLE pdp_domain_package_version (
          id BINARY(16) PRIMARY KEY, package_id BINARY(16) NOT NULL,
          semantic_version VARCHAR(50) NOT NULL, manifest CLOB NOT NULL,
          content_hash VARCHAR(128) NOT NULL, status VARCHAR(30) NOT NULL,
          frozen BOOLEAN NOT NULL, created_by BINARY(16) NOT NULL,
          reviewed_by BINARY(16), approved_by BINARY(16), revision BIGINT NOT NULL,
          created_at TIMESTAMP NOT NULL,
          CONSTRAINT uk_package_semantic_version UNIQUE (package_id, semantic_version),
          CONSTRAINT uk_package_content_hash UNIQUE (package_id, content_hash)
        )
        """);
    jdbc.execute(
        """
        CREATE TABLE pdp_domain_package_snapshot (
          id BINARY(16) PRIMARY KEY, package_version_id BINARY(16) NOT NULL,
          layer_chain CLOB NOT NULL, snapshot CLOB NOT NULL,
          content_hash VARCHAR(128) NOT NULL, created_at TIMESTAMP NOT NULL
        )
        """);
    jdbc.execute(
        """
        CREATE TABLE pdp_domain_package_migration (
          id BINARY(16) PRIMARY KEY, source_version_id BINARY(16) NOT NULL,
          target_version_id BINARY(16) NOT NULL, preview_id BINARY(16) NOT NULL,
          status VARCHAR(30) NOT NULL, batch_size INTEGER NOT NULL,
          migrated_count BIGINT NOT NULL, failed_count BIGINT NOT NULL,
          failure_details CLOB, rollback_plan CLOB NOT NULL,
          revision BIGINT NOT NULL, created_at TIMESTAMP NOT NULL
        )
        """);
  }

  private static SqlSessionFactory sessionFactory(DataSource dataSource) throws Exception {
    SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
    bean.setDataSource(dataSource);
    bean.setMapperLocations(
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/domainconfig/*Mapper.xml"));
    return bean.getObject();
  }

  private static HikariDataSource dataSource(MySQLContainer<?> mysql) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(mysql.getJdbcUrl());
    config.setUsername(mysql.getUsername());
    config.setPassword(mysql.getPassword());
    config.setDriverClassName(mysql.getDriverClassName());
    config.setMaximumPoolSize(3);
    config.setAutoCommit(false);
    config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
    return new HikariDataSource(config);
  }

  private static void applyChangelog(DataSource dataSource, String changelog) throws Exception {
    try (var connection = dataSource.getConnection()) {
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (Liquibase liquibase =
          new Liquibase(
              changelog,
              new ClassLoaderResourceAccessor(
                  DomainPackageDatabaseContractTest.class.getClassLoader()),
              database)) {
        liquibase.update(new Contexts());
      }
    }
  }

  private static String resource(String path) throws Exception {
    try (var stream =
        DomainPackageDatabaseContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("resource not found: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static UUID uuid(long suffix) {
    return UUID.fromString("01900000-0000-7000-8000-" + "%012d".formatted(suffix));
  }

  private record Fixture(
      UUID workspaceId,
      DomainPackage platform,
      DomainPackage industry,
      DomainPackage customer,
      DomainPackageVersion v100,
      DomainPackageVersion v110,
      DomainPackageVersion customerVersion,
      UUID snapshotId) {}
}
