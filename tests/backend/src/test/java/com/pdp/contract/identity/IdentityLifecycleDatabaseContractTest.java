package com.pdp.contract.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pdp.identity.application.IdentityLifecycleService;
import com.pdp.identity.domain.ExternalIdentity;
import com.pdp.identity.domain.UserAccount;
import com.pdp.identity.domain.UserSession;
import com.pdp.mysql.identity.ExternalIdentityMapper;
import com.pdp.mysql.identity.MysqlExternalIdentityRepository;
import com.pdp.mysql.identity.MysqlUserAccountRepository;
import com.pdp.mysql.identity.MysqlUserSessionRepository;
import com.pdp.mysql.identity.UserAccountMapper;
import com.pdp.mysql.identity.UserSessionMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.MySQLContainer;

class IdentityLifecycleDatabaseContractTest {

  private static final Instant NOW = Instant.parse("2026-07-17T08:00:00Z");

  @Test
  void commonSchemaAndMapperContractsRemainDatabaseNeutralAndExplicit() throws Exception {
    JdbcDataSource dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:identity-contract;DB_CLOSE_DELAY=-1");
    applyChangelog(dataSource, "db/changelog/db.changelog-master.xml");

    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN (
                  'PDP_USER_ACCOUNT', 'PDP_EXTERNAL_IDENTITY', 'PDP_USER_SESSION'
                )
                """,
                Integer.class))
        .isEqualTo(3);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_NAME = 'UK_EXTERNAL_IDENTITY_ISSUER_SUBJECT'
                """,
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
                WHERE CONSTRAINT_NAME IN (
                  'FK_EXTERNAL_IDENTITY_USER', 'FK_USER_SESSION_USER'
                )
                """,
                Integer.class))
        .isEqualTo(2);

    String accountMapper = resource("mapper/identity/UserAccountMapper.xml");
    String externalMapper = resource("mapper/identity/ExternalIdentityMapper.xml");
    String sessionMapper = resource("mapper/identity/UserSessionMapper.xml");
    assertThat(accountMapper).contains("UuidBinaryTypeHandler", "revision = revision + 1");
    assertThat(externalMapper)
        .contains("findByIssuerAndSubject", "issuer = #{issuer} AND subject = #{subject}")
        .doesNotContain("SELECT *");
    assertThat(sessionMapper)
        .contains("status = 'ACTIVE'", "expires_at &gt; CURRENT_TIMESTAMP(6)")
        .doesNotContain("SELECT *");
  }

  @Test
  void mysqlLifecyclePersistsIdentityUniquenessAndRevokesActiveSessions() throws Exception {
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

        UUID userId = UUID.fromString("01900000-0000-7000-8000-000000000101");
        UUID secondUserId = UUID.fromString("01900000-0000-7000-8000-000000000102");
        UUID sessionId = UUID.fromString("01900000-0000-7000-8000-000000000103");
        String issuer = "https://idp.example.com";
        String subject = "enterprise-user-101";

        try (var session = sessionFactory.openSession(false)) {
          var accounts =
              new MysqlUserAccountRepository(session.getMapper(UserAccountMapper.class));
          var identities =
              new MysqlExternalIdentityRepository(session.getMapper(ExternalIdentityMapper.class));
          var sessions =
              new MysqlUserSessionRepository(session.getMapper(UserSessionMapper.class));

          UserAccount account =
              UserAccount.invited(userId, subject, "用户甲", "user101@example.com").activate(NOW);
          accounts.save(account);
          accounts.save(
              UserAccount.invited(
                      secondUserId, "enterprise-user-102", "用户乙", "user102@example.com")
                  .activate(NOW));
          identities.save(
              new ExternalIdentity(
                  UUID.fromString("01900000-0000-7000-8000-000000000104"),
                  userId,
                  issuer,
                  subject,
                  account.email(),
                  true,
                  ExternalIdentity.Status.ACTIVE,
                  NOW,
                  NOW));
          sessions.save(
              UserSession.active(
                  sessionId,
                  userId,
                  "idp-session-101",
                  "vault://refresh/101",
                  NOW.minusSeconds(60),
                  NOW.plusSeconds(315_360_000),
                  account.authorizationVersion()));
          session.commit();

          assertThat(accounts.findByExternalSubject(subject)).contains(account);
          assertThat(identities.findByIssuerAndSubject(issuer, subject))
              .hasValueSatisfying(identity -> assertThat(identity.userId()).isEqualTo(userId));

          assertThatThrownBy(
                  () ->
                      identities.save(
                          new ExternalIdentity(
                              UUID.fromString("01900000-0000-7000-8000-000000000105"),
                              secondUserId,
                              issuer,
                              subject,
                              "duplicate@example.com",
                              true,
                              ExternalIdentity.Status.ACTIVE,
                              NOW,
                              NOW)))
              .isInstanceOf(org.apache.ibatis.exceptions.PersistenceException.class);
          session.rollback();
        }

        try (var session = sessionFactory.openSession(false)) {
          var accounts =
              new MysqlUserAccountRepository(session.getMapper(UserAccountMapper.class));
          UserSessionMapper sessionMapper = session.getMapper(UserSessionMapper.class);
          var sessions = new MysqlUserSessionRepository(sessionMapper);
          var revokedCredentials = new ArrayList<String>();
          var revocations = new ArrayList<com.pdp.identity.application.AuthorizationRevoked>();
          var service =
              new IdentityLifecycleService(
                  accounts,
                  sessions,
                  revokedCredentials::add,
                  revocations::add,
                  Clock.fixed(NOW, ZoneOffset.UTC));

          UserAccount disabled = service.disable(userId, "离职");
          session.commit();

          assertThat(disabled.status()).isEqualTo(UserAccount.Status.DISABLED);
          assertThat(disabled.authorizationVersion()).isEqualTo(1);
          assertThat(sessions.findActiveByUserId(userId)).isEmpty();
          assertThat(sessionMapper.findById(sessionId).status())
              .isEqualTo(UserSession.Status.REVOKED);
          assertThat(sessionMapper.findById(sessionId).revocationReason()).isEqualTo("离职");
          assertThat(revokedCredentials).containsExactly("vault://refresh/101");
          assertThat(revocations).singleElement();
        }
      }
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
            .getResources("classpath*:mapper/identity/*Mapper.xml"));
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
                  IdentityLifecycleDatabaseContractTest.class.getClassLoader()),
              database)) {
        liquibase.update(new Contexts());
      }
    }
  }

  private static String resource(String path) throws Exception {
    try (var stream =
        IdentityLifecycleDatabaseContractTest.class.getClassLoader().getResourceAsStream(path)) {
      if (stream == null) {
        throw new IllegalStateException("resource not found: " + path);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
