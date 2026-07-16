package com.pdp.contract.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.mysql.workspace.CollaborationGrantMapper;
import com.pdp.mysql.workspace.DataScopeMapper;
import com.pdp.mysql.workspace.MysqlCollaborationGrantRepository;
import com.pdp.mysql.workspace.MysqlDataScopeRepository;
import com.pdp.mysql.workspace.MysqlOrganizationUnitRepository;
import com.pdp.mysql.workspace.MysqlWorkspaceMembershipRepository;
import com.pdp.mysql.workspace.MysqlWorkspaceRepository;
import com.pdp.mysql.workspace.MysqlWorkspaceRoleRepository;
import com.pdp.mysql.workspace.OrganizationUnitMapper;
import com.pdp.mysql.workspace.WorkspaceMapper;
import com.pdp.mysql.workspace.WorkspaceMembershipMapper;
import com.pdp.mysql.workspace.WorkspaceRoleMapper;
import com.pdp.workspace.domain.CollaborationGrant;
import com.pdp.workspace.domain.DataScope;
import com.pdp.workspace.domain.OrganizationUnit;
import com.pdp.workspace.domain.Workspace;
import com.pdp.workspace.domain.WorkspaceMembership;
import com.pdp.workspace.domain.WorkspaceRole;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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

class WorkspaceGovernanceDatabaseContractTest {

  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");
  private static final Instant FAR_FUTURE = Instant.parse("2036-07-17T08:00:00Z");

  @Test
  void commonSchemaAndMapperContractsDefineIsolationHierarchyAndKeysetPagination()
      throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:workspace-contract;DB_CLOSE_DELAY=-1");
    applyChangelog(dataSource, "db/changelog/db.changelog-master.xml");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN (
                  'PDP_WORKSPACE', 'PDP_ORGANIZATION_UNIT', 'PDP_WORKSPACE_ROLE',
                  'PDP_DATA_SCOPE', 'PDP_WORKSPACE_MEMBERSHIP',
                  'PDP_COLLABORATION_GRANT'
                )
                """,
                Integer.class))
        .isEqualTo(6);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_NAME IN (
                  'UK_ORG_WORKSPACE_CODE', 'UK_ROLE_WORKSPACE_KEY',
                  'UK_SCOPE_WORKSPACE_KEY',
                  'UK_MEMBERSHIP_WORKSPACE_USER'
                )
                """,
                Integer.class))
        .isEqualTo(4);

    String workspaceMapper = resource("mapper/workspace/WorkspaceMapper.xml");
    String organizationMapper = resource("mapper/workspace/OrganizationUnitMapper.xml");
    String roleMapper = resource("mapper/workspace/WorkspaceRoleMapper.xml");
    String scopeMapper = resource("mapper/workspace/DataScopeMapper.xml");
    String membershipMapper = resource("mapper/workspace/WorkspaceMembershipMapper.xml");
    String grantMapper = resource("mapper/workspace/CollaborationGrantMapper.xml");

    assertThat(workspaceMapper)
        .contains(
            "findAccessibleByUserId",
            "m.workspace_id = w.id",
            "m.user_id =",
            "ORDER BY w.code, w.id",
            "findPageAfter",
            "LIMIT #{limit}")
        .doesNotContain("OFFSET", "SELECT *");
    assertThat(organizationMapper)
        .contains(
            "workspace_id =",
            "ORDER BY path, id",
            "path &gt; #{afterPath}",
            "id &gt;",
            "LIMIT #{limit}")
        .doesNotContain("OFFSET", "SELECT *");
    assertThat(roleMapper + scopeMapper + membershipMapper + grantMapper)
        .contains(
            "JsonDocumentTypeHandler",
            "UuidBinaryTypeHandler",
            "revision = #{revision}",
            "AND revision = #{revision} - 1")
        .doesNotContain("SELECT *");
    assertThat(grantMapper)
        .contains(
            "owner_workspace_id =",
            "collaborator_workspace_id =",
            "valid_from &lt;=",
            "valid_until &gt;");
  }

  @Test
  void mysqlEnforcesUniquenessAndPersistsHierarchyDataScopesAndKeysetPages()
      throws Exception {
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
        SqlSessionFactory sessionFactory = sessionFactory(dataSource);
        Fixture fixture = persistFixture(sessionFactory);

        verifyUniqueness(sessionFactory, fixture);
        verifyQueriesAndKeysetPagination(sessionFactory, fixture);
      }
    }
  }

  private static Fixture persistFixture(SqlSessionFactory sessionFactory) {
    UUID ownerUserId = uuid(1);
    UUID collaboratorOwnerId = uuid(2);
    UUID memberUserId = uuid(3);
    Workspace owner =
        Workspace.draft(uuid(10), "OWNER", "主工作空间", ownerUserId, "zh-CN", "Asia/Shanghai", NOW)
            .activate(NOW);
    Workspace collaborator =
        Workspace.draft(
                uuid(11),
                "COLLAB",
                "协作工作空间",
                collaboratorOwnerId,
                "zh-CN",
                "Asia/Shanghai",
                NOW)
            .activate(NOW);
    OrganizationUnit root =
        OrganizationUnit.create(
            owner.id(), null, "HQ", "总部", OrganizationUnit.Type.COMPANY, "CN");
    OrganizationUnit child =
        OrganizationUnit.create(
            owner.id(), root, "ENG", "研发部", OrganizationUnit.Type.DEPARTMENT, "CN-SH");
    WorkspaceRole role =
        WorkspaceRole.create(
            owner.id(), "project-manager", "项目经理", Set.of("project.read", "project.update"));
    DataScope scope =
        DataScope.create(
            owner.id(),
            "cn-projects",
            "中国项目",
            Set.of("PROJECT", "DELIVERABLE"),
            Map.of("region", "CN", "includeChildren", true));
    WorkspaceMembership membership =
        WorkspaceMembership.active(
            owner.id(),
            memberUserId,
            child.id(),
            WorkspaceMembership.Type.INTERNAL,
            NOW,
            null,
            Set.of(role.id()),
            Set.of(scope.id()));
    CollaborationGrant grant =
        CollaborationGrant.draft(
                owner.id(),
                collaborator.id(),
                "PROJECT",
                uuid(30),
                role.id(),
                Set.of("project.read"),
                NOW,
                FAR_FUTURE,
                ownerUserId)
            .activate();

    try (var session = sessionFactory.openSession(false)) {
      new MysqlWorkspaceRepository(session.getMapper(WorkspaceMapper.class)).save(owner);
      new MysqlWorkspaceRepository(session.getMapper(WorkspaceMapper.class)).save(collaborator);
      new MysqlOrganizationUnitRepository(session.getMapper(OrganizationUnitMapper.class))
          .save(root);
      new MysqlOrganizationUnitRepository(session.getMapper(OrganizationUnitMapper.class))
          .save(child);
      new MysqlWorkspaceRoleRepository(session.getMapper(WorkspaceRoleMapper.class)).save(role);
      new MysqlDataScopeRepository(session.getMapper(DataScopeMapper.class)).save(scope);
      new MysqlWorkspaceMembershipRepository(session.getMapper(WorkspaceMembershipMapper.class))
          .save(membership);
      new MysqlCollaborationGrantRepository(session.getMapper(CollaborationGrantMapper.class))
          .save(grant);
      session.commit();
    }
    return new Fixture(
        owner, collaborator, root, child, role, scope, membership, grant, memberUserId);
  }

  private static void verifyUniqueness(SqlSessionFactory sessionFactory, Fixture fixture) {
    try (var session = sessionFactory.openSession(false)) {
      var workspaces = new MysqlWorkspaceRepository(session.getMapper(WorkspaceMapper.class));
      Workspace duplicateCode =
          Workspace.draft(
              uuid(12),
              fixture.owner().code(),
              "重复编码",
              uuid(4),
              "zh-CN",
              "Asia/Shanghai",
              NOW);
      assertThatThrownBy(() -> workspaces.save(duplicateCode))
          .isInstanceOf(PersistenceException.class);
      session.rollback();
    }

    try (var session = sessionFactory.openSession(false)) {
      var organizations =
          new MysqlOrganizationUnitRepository(session.getMapper(OrganizationUnitMapper.class));
      OrganizationUnit duplicateOrganization =
          OrganizationUnit.create(
              fixture.owner().id(),
              null,
              fixture.root().code(),
              "重复总部",
              OrganizationUnit.Type.COMPANY,
              "CN");
      assertThatThrownBy(() -> organizations.save(duplicateOrganization))
          .isInstanceOf(PersistenceException.class);
      session.rollback();
    }
  }

  private static void verifyQueriesAndKeysetPagination(
      SqlSessionFactory sessionFactory, Fixture fixture) {
    try (var session = sessionFactory.openSession(true)) {
      WorkspaceMapper workspaceMapper = session.getMapper(WorkspaceMapper.class);
      OrganizationUnitMapper organizationMapper =
          session.getMapper(OrganizationUnitMapper.class);
      var workspaces = new MysqlWorkspaceRepository(workspaceMapper);
      var organizations = new MysqlOrganizationUnitRepository(organizationMapper);
      var roles = new MysqlWorkspaceRoleRepository(session.getMapper(WorkspaceRoleMapper.class));
      var scopes = new MysqlDataScopeRepository(session.getMapper(DataScopeMapper.class));
      var memberships =
          new MysqlWorkspaceMembershipRepository(
              session.getMapper(WorkspaceMembershipMapper.class));
      var grants =
          new MysqlCollaborationGrantRepository(
              session.getMapper(CollaborationGrantMapper.class));

      assertThat(workspaces.findAccessibleByUserId(fixture.memberUserId()))
          .extracting(Workspace::id)
          .containsExactly(fixture.owner().id());
      assertThat(organizations.findOrganizationsByWorkspaceId(fixture.owner().id()))
          .extracting(OrganizationUnit::path)
          .containsExactly("/HQ", "/HQ/ENG");
      assertThat(roles.findRoleByStableKey(fixture.owner().id(), fixture.role().stableKey()))
          .contains(fixture.role());
      assertThat(scopes.findDataScopeByStableKey(fixture.owner().id(), fixture.scope().stableKey()))
          .hasValueSatisfying(
              scope -> {
                assertThat(scope.resourceTypes()).containsExactlyInAnyOrder("PROJECT", "DELIVERABLE");
                assertThat(scope.condition())
                    .containsEntry("region", "CN")
                    .containsEntry("includeChildren", true);
              });
      assertThat(
              memberships.findByWorkspaceAndUser(
                  fixture.owner().id(), fixture.memberUserId()))
          .contains(fixture.membership());
      assertThat(
              grants.findActiveGrants(
                  fixture.owner().id(), fixture.collaborator().id(), NOW.plusSeconds(1)))
          .containsExactly(fixture.grant());

      var firstWorkspacePage = workspaceMapper.findPageAfter(null, null, 1);
      assertThat(firstWorkspacePage).hasSize(1);
      var lastWorkspace = firstWorkspacePage.getFirst();
      assertThat(
              workspaceMapper.findPageAfter(
                  lastWorkspace.code(), lastWorkspace.id(), 1))
          .singleElement()
          .satisfies(row -> assertThat(row.id()).isNotEqualTo(lastWorkspace.id()));

      var firstOrganizationPage =
          organizationMapper.findPageAfter(fixture.owner().id(), null, null, 1);
      assertThat(firstOrganizationPage).hasSize(1);
      var lastOrganization = firstOrganizationPage.getFirst();
      assertThat(
              organizationMapper.findPageAfter(
                  fixture.owner().id(),
                  lastOrganization.path(),
                  lastOrganization.id(),
                  1))
          .extracting(row -> row.id())
          .containsExactly(fixture.child().id());
    }
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

  private static SqlSessionFactory sessionFactory(HikariDataSource dataSource) throws Exception {
    SqlSessionFactoryBean bean = new SqlSessionFactoryBean();
    bean.setDataSource(dataSource);
    bean.setMapperLocations(
        new PathMatchingResourcePatternResolver()
            .getResources("classpath*:mapper/workspace/*Mapper.xml"));
    return bean.getObject();
  }

  private static void applyChangelog(javax.sql.DataSource dataSource, String changelog)
      throws Exception {
    try (var connection = dataSource.getConnection()) {
      var database =
          DatabaseFactory.getInstance()
              .findCorrectDatabaseImplementation(new JdbcConnection(connection));
      try (Liquibase liquibase =
          new Liquibase(
              changelog,
              new ClassLoaderResourceAccessor(
                  WorkspaceGovernanceDatabaseContractTest.class.getClassLoader()),
              database)) {
        liquibase.update(new Contexts());
      }
    }
  }

  private static String resource(String path) throws Exception {
    try (var stream =
        WorkspaceGovernanceDatabaseContractTest.class.getClassLoader().getResourceAsStream(path)) {
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
      Workspace owner,
      Workspace collaborator,
      OrganizationUnit root,
      OrganizationUnit child,
      WorkspaceRole role,
      DataScope scope,
      WorkspaceMembership membership,
      CollaborationGrant grant,
      UUID memberUserId) {}
}
