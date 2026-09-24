package in.dukkan.config;

import in.dukkan.security.JwtAuthEntryPoint;
import in.dukkan.security.JwtAuthFilter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Keep in sync with {@link JwtAuthFilter} public-path checks. */
    public static final String[] PUBLIC_PATHS = {
        "/api/health",
        "/api/auth/login",
        "/api/auth/signup",
        "/api/auth/otp/request",
        "/api/auth/otp/verify",
        "/api/auth/forgot-password",
        "/api/auth/reset-password",
        "/error"
    };

    /** Anonymous storefront discovery. Keep in sync with {@link JwtAuthFilter}. */
    public static final String[] PUBLIC_GET_PATHS = {
        "/api/categories",
        "/api/neighborhoods",
        "/api/shops/**",
        "/api/catalog/**",
        "/api/listings/**",
        "/api/ads",
        "/api/settings",
        "/api/partners",
        "/api/reviews",
        "/api/coupons",
        "/api/geo/**",
        "/api/search",
        "/api/providers/**",
        "/api/services/**"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http, JwtAuthFilter jwtAuthFilter, JwtAuthEntryPoint jwtAuthEntryPoint)
            throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.cors(Customizer.withDefaults());
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.exceptionHandling(ex -> ex.authenticationEntryPoint(jwtAuthEntryPoint));
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**")
                .permitAll()
                .requestMatchers(PUBLIC_PATHS)
                .permitAll()
                .requestMatchers("/uploads/**")
                .permitAll()
                .requestMatchers(HttpMethod.GET, PUBLIC_GET_PATHS)
                .permitAll()
                .requestMatchers("/api/admin/**")
                .hasRole("ADMIN")
                .anyRequest()
                .authenticated());
        http.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(
                java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
