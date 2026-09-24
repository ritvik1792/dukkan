package in.dukkan.config;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code local} on docker-compose Postgres and {@code prod} off localhost.
 */
@Component
public class ProfileDatasourceGuard {

    public ProfileDatasourceGuard(Environment env, @Value("${spring.datasource.url}") String url) {
        String normalized = url == null ? "" : url.toLowerCase(Locale.ROOT);
        boolean localhost = normalized.contains("localhost") || normalized.contains("127.0.0.1");
        if (env.matchesProfiles("local") && !localhost) {
            throw new IllegalStateException(
                    "Profile 'local' must use localhost Postgres (docker compose), not Cloud SQL.");
        }
        if (env.matchesProfiles("prod") && localhost) {
            throw new IllegalStateException(
                    "Profile 'prod' must use Cloud SQL via DUKKAN_DB_URL, not localhost.");
        }
        if (env.matchesProfiles("prod")) {
            String secret = env.getProperty("app.jwt.secret", "");
            if (secret.length() < 32 || secret.contains("change-before-production")) {
                throw new IllegalStateException(
                        "Profile 'prod' requires DUKKAN_JWT_SECRET (32+ characters) from the environment or Secret Manager.");
            }
        }
    }
}
