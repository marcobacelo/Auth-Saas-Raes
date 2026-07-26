package com.auth.saas.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.platform")
public record PlatformProperties(String username, String password) {
}
