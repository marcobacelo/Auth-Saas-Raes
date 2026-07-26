package com.auth.saas.infra.provisioning;

import com.auth.saas.domain.tenant.ProvisionTenant;
import com.auth.saas.domain.tenant.ProvisionedTenant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantProvisioningService {

    private final ProvisionTenant provisionTenant;

    public TenantProvisioningService(ProvisionTenant provisionTenant) {
        this.provisionTenant = provisionTenant;
    }

    @Transactional
    public ProvisionedTenant provision(String slug, String username, String password) {
        return provisionTenant.provision(slug, username, password);
    }
}
