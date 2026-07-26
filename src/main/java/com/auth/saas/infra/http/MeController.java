package com.auth.saas.infra.http;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.auth.GetAuthenticatedIdentity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/t/{tenantSlug}/v1")
public class MeController {

    private final GetAuthenticatedIdentity getAuthenticatedIdentity;

    public MeController(GetAuthenticatedIdentity getAuthenticatedIdentity) {
        this.getAuthenticatedIdentity = getAuthenticatedIdentity;
    }

    @GetMapping("/me")
    public MeResponse me(@PathVariable String tenantSlug, @AuthenticationPrincipal Jwt jwt) {
        UUID subjectId = requireUuidClaim(jwt.getSubject());
        UUID tokenTenantId = requireUuidClaim(jwt.getClaimAsString("tid"));
        var identity = getAuthenticatedIdentity.get(tenantSlug, subjectId, tokenTenantId);
        return new MeResponse(identity.sub().toString(), identity.username(), identity.tid().toString());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomain(DomainException ex) {
        HttpStatus status = GetAuthenticatedIdentity.TENANT_NOT_FOUND.equals(ex.code())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(Map.of("code", ex.code()));
    }

    private static UUID requireUuidClaim(String value) {
        if (value == null || value.isBlank()) {
            throw new DomainException(GetAuthenticatedIdentity.INVALID_TOKEN);
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new DomainException(GetAuthenticatedIdentity.INVALID_TOKEN);
        }
    }

    public record MeResponse(String sub, String username, String tid) {
    }
}
