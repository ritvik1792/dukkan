package in.dukkan.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProfileDatasourceGuardTest {

    @Test
    void localAcceptsLocalhostAndRejectsCloudSql() {
        MockEnvironment local = new MockEnvironment();
        local.setActiveProfiles("local");
        assertDoesNotThrow(
                () -> new ProfileDatasourceGuard(local, "jdbc:postgresql://localhost:5432/dukkan"));
        assertThrows(
                IllegalStateException.class,
                () -> new ProfileDatasourceGuard(
                        local,
                        "jdbc:postgresql:///dukkan?cloudSqlInstance=p:r:i&socketFactory=com.google.cloud.sql.postgres.SocketFactory"));
    }

    @Test
    void prodAcceptsCloudSqlAndRejectsLocalhost() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        prod.setProperty("app.jwt.secret", "production-jwt-secret-that-is-long-enough-32b");
        assertDoesNotThrow(
                () -> new ProfileDatasourceGuard(
                        prod,
                        "jdbc:postgresql:///dukkan?cloudSqlInstance=p:r:i&socketFactory=com.google.cloud.sql.postgres.SocketFactory"));
        assertThrows(
                IllegalStateException.class,
                () -> new ProfileDatasourceGuard(prod, "jdbc:postgresql://localhost:5432/dukkan"));
    }
}
