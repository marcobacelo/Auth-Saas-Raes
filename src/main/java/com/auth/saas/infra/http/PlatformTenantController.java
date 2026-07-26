package com.auth.saas.infra.http;

import com.auth.saas.domain.DomainException;
import com.auth.saas.domain.tenant.ProvisionTenant;
import com.auth.saas.domain.tenant.ProvisionedTenant;
import com.auth.saas.infra.provisioning.TenantProvisioningService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/platform/v1")
public class PlatformTenantController {

    private final TenantProvisioningService tenantProvisioningService;

    public PlatformTenantController(TenantProvisioningService tenantProvisioningService) {
        this.tenantProvisioningService = tenantProvisioningService;
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    public ProvisionResponse provision(@Valid @RequestBody ProvisionRequest request) {
        ProvisionedTenant created = tenantProvisioningService.provision(
                request.slug(), request.username(), request.password());
        return new ProvisionResponse(
                created.tenantId().toString(),
                created.slug(),
                created.identityId().toString(),
                created.username());
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomain(DomainException ex) {
        HttpStatus status = ProvisionTenant.TENANT_EXISTS.equals(ex.code())
                ? HttpStatus.CONFLICT
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(Map.of("code", ex.code()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Map<String, String>> handleInvalidRequest(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("code", ProvisionTenant.INVALID_REQUEST));
    }

    public record ProvisionRequest(
            @NotBlank String slug,
            @NotBlank String username,
            @NotBlank @Size(min = 12) String password) {
    }

    public record ProvisionResponse(String tenantId, String slug, String identityId, String username) {
    }
}
