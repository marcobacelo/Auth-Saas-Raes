package com.auth.saas.infra.config;

import com.auth.saas.infra.token.RsaKeyMaterial;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.nio.charset.StandardCharsets;

@Configuration
public class SecurityConfig {

    public static final String INVALID_TOKEN = "INVALID_TOKEN";

    private static final byte[] INVALID_TOKEN_BODY =
            "{\"code\":\"INVALID_TOKEN\"}".getBytes(StandardCharsets.UTF_8);

    @Bean
    JwtDecoder jwtDecoder(RsaKeyMaterial keyMaterial) {
        return NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey()).build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        AuthenticationEntryPoint invalidTokenEntryPoint = (request, response, authException) -> {
            response.setStatus(401);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(INVALID_TOKEN_BODY);
        };

        http
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
}
