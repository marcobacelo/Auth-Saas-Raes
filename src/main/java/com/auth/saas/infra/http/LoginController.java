package com.auth.saas.infra.http;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.auth.AccessToken;
import com.auth.saas.domain.auth.AuthenticateWithPassword;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/t/{tenantSlug}/v1/auth")
public class LoginController {

    private final AuthenticateWithPassword authenticateWithPassword;

    public LoginController(AuthenticateWithPassword authenticateWithPassword) {
        this.authenticateWithPassword = authenticateWithPassword;
    }

    @PostMapping("/login")
    public LoginResponse login(
            @PathVariable String tenantSlug,
            @Valid @RequestBody LoginRequest request) {
        AccessToken token = authenticateWithPassword.authenticate(
                tenantSlug, request.username(), request.password());
        return new LoginResponse(token.value());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomain(DomainException ex) {
        HttpStatus status = AuthenticateWithPassword.TENANT_NOT_FOUND.equals(ex.code())
                ? HttpStatus.NOT_FOUND
                : HttpStatus.UNAUTHORIZED;
        return ResponseEntity.status(status).body(Map.of("code", ex.code()));
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record LoginResponse(String accessToken) {
    }
}
