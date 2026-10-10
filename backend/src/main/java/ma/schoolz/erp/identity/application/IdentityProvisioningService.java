package ma.schoolz.erp.identity.application;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SecureTokenService;
import ma.schoolz.erp.identity.IdentityProvisioning;
import ma.schoolz.erp.identity.domain.AccountStatus;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityProvisioningService implements IdentityProvisioning {

    private final IdentityTenantRepository tenants;
    private final UserAccountRepository accounts;
    private final SystemRoleProvisioner roleProvisioner;
    private final AuthenticationService authentication;
    private final SecureTokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public IdentityProvisioningService(
            IdentityTenantRepository tenants,
            UserAccountRepository accounts,
            SystemRoleProvisioner roleProvisioner,
            AuthenticationService authentication,
            SecureTokenService tokenService,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.roleProvisioner = roleProvisioner;
        this.authentication = authentication;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SchoolIdentity createSchoolIdentity(
            String tenantSlug,
            String schoolName,
            String ownerName,
            String ownerEmail,
            String locale) {
        String slug = IdentityTenant.normalizeSlug(tenantSlug);
        if (tenants.existsBySlug(slug)) {
            throw new IdentityProvisioning.ProvisioningException(
                    "The school identifier is already in use");
        }
        var now = clock.instant();
        IdentityTenant tenant = tenants.save(IdentityTenant.create(slug, schoolName, now));
        Map<String, Role> roles = roleProvisioner.provision(tenant);
        UserAccount owner =
                UserAccount.create(
                        tenant.id(),
                        ownerEmail,
                        ownerName,
                        passwordEncoder.encode(tokenService.generate()),
                        locale,
                        false,
                        now);
        owner.assignRoles(Set.of(roles.get("ORGANIZATION_OWNER")), now);
        owner = accounts.save(owner);
        return view(tenant, owner);
    }

    @Override
    @Transactional
    public SchoolIdentity schoolIdentity(UUID tenantId, UUID ownerUserId) {
        IdentityTenant tenant =
                tenants.findById(tenantId)
                        .orElseThrow(
                                () ->
                                        new IdentityProvisioning.ProvisioningException(
                                                "School identity is incomplete"));
        UserAccount owner =
                accounts.findByIdAndTenantId(ownerUserId, tenantId)
                        .orElseThrow(
                                () ->
                                        new IdentityProvisioning.ProvisioningException(
                                                "School identity is incomplete"));
        return view(tenant, owner);
    }

    private SchoolIdentity view(IdentityTenant tenant, UserAccount owner) {
        String activationToken =
                owner.status() == AccountStatus.PENDING_VERIFICATION
                        ? authentication.createAccountActivationToken(owner)
                        : null;
        return new SchoolIdentity(
                tenant.id(),
                tenant.slug(),
                tenant.displayName(),
                owner.id(),
                owner.email(),
                owner.status().name(),
                activationToken);
    }
}
