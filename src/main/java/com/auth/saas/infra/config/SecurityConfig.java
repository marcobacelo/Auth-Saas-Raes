package com.auth.saas.infra.config;

import com.auth.saas.infra.token.RsaKeyMaterial;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    public static final String INVALID_TOKEN = "INVALID_TOKEN";
    public static final String PLATFORM_UNAUTHORIZED = "PLATFORM_UNAUTHORIZED";

    private static final byte[] INVALID_TOKEN_BODY =
            "{\"code\":\"INVALID_TOKEN\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PLATFORM_UNAUTHORIZED_BODY =
            "{\"code\":\"PLATFORM_UNAUTHORIZED\"}".getBytes(StandardCharsets.UTF_8);

    @Bean
    JwtDecoder jwtDecoder(RsaKeyMaterial keyMaterial) {
        return NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey()).build();
    }

    @Bean
    UserDetailsService platformUserDetailsService(PlatformProperties platformProperties) {
        return new InMemoryUserDetailsManager(
                User.withUsername(platformProperties.username())
                        .password("{noop}" + platformProperties.password())
                        .roles("PLATFORM")
                        .build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain platformSecurityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint platformUnauthorized = (request, response, authException) -> {
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(PLATFORM_UNAUTHORIZED_BODY);
        };

        http
                .securityMatcher("/platform/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/platform/v1/tenants").authenticated()
                        .anyRequest().denyAll())
                .httpBasic(basic -> basic.authenticationEntryPoint(platformUnauthorized))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(platformUnauthorized));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain tenantSecurityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint invalidTokenEntryPoint = (request, response, authException) -> {
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(INVALID_TOKEN_BODY);
        };

        http
                .securityMatcher("/t/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/t/*/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/t/*/v1/me").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(invalidTokenEntryPoint))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(invalidTokenEntryPoint));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain denyAllSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().denyAll());
        return http.build();
    }
}
