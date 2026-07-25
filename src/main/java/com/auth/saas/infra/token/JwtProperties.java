package com.auth.saas.infra.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(long accessTokenTtlSeconds, boolean ephemeralKeys) {

    public JwtProperties {
        if (accessTokenTtlSeconds <= 0) {
            accessTokenTtlSeconds = 900;
        }
    }
}
