package ma.schoolz.erp.identity.security;

import java.util.List;
import ma.schoolz.erp.foundation.security.IdentitySecurityProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(IdentitySecurityProperties.class)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, AccessTokenFilter accessTokenFilter)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(
                        sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(
                        headers ->
                                headers.contentTypeOptions(Customizer.withDefaults())
                                        .frameOptions(frame -> frame.deny())
                                        .contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src"
                                                                    + " 'self'; style-src 'self'"
                                                                    + " 'unsafe-inline'; img-src"
                                                                    + " 'self' data:; font-src"
                                                                    + " 'self'; connect-src 'self';"
                                                                    + " object-src 'none'; base-uri"
                                                                    + " 'self'; form-action 'self';"
                                                                    + " frame-ancestors 'none'"))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Cross-Origin-Opener-Policy",
                                                        "same-origin"))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "Cross-Origin-Resource-Policy",
                                                        "same-origin"))
                                        .addHeaderWriter(
                                                new StaticHeadersWriter(
                                                        "X-Permitted-Cross-Domain-Policies",
                                                        "none")))
                .exceptionHandling(
                        exceptions ->
                                exceptions
                                        .authenticationEntryPoint(
                                                (request, response, exception) -> {
                                                    response.setStatus(401);
                                                    response.setContentType(
                                                            MediaType
                                                                    .APPLICATION_PROBLEM_JSON_VALUE);
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"type\":\"about:blank\",\"title\":\"Unauthorized\",\"status\":401}");
                                                })
                                        .accessDeniedHandler(
                                                (request, response, exception) -> {
                                                    response.setStatus(403);
                                                    response.setContentType(
                                                            MediaType
                                                                    .APPLICATION_PROBLEM_JSON_VALUE);
                                                    response.getWriter()
                                                            .write(
                                                                    "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403}");
                                                }))
                .authorizeHttpRequests(
                        requests ->
                                requests.requestMatchers(HttpMethod.OPTIONS, "/**")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/system/status",
                                                "/actuator/health",
                                                "/actuator/health/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/login",
                                                "/api/v1/auth/refresh",
                                                "/api/v1/auth/logout",
                                                "/api/v1/auth/forgot-password",
                                                "/api/v1/auth/reset-password",
                                                "/api/v1/auth/activate-account",
                                                "/api/v1/auth/verify-email")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/platform/provisioning/schools")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/admissions/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/enrollments/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/childcare/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/attendance/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/homework/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/family-portal/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/finance/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/communications/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/documents/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/document-signatures/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/transport/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/cafeteria/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/activities/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/public/library/**")
                                        .permitAll()
                                        .requestMatchers("/api/v1/payments/webhooks/**")
                                        .permitAll()
                                        .requestMatchers("/api/public/v1/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .addFilterBefore(accessTokenFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${schoolz.security.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "X-Correlation-Id", "X-SchoolZ-Api-Key"));
        configuration.setExposedHeaders(
                List.of(
                        "X-Correlation-Id",
                        "X-Request-Id",
                        "X-RateLimit-Limit",
                        "X-RateLimit-Remaining"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
