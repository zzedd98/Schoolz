package ma.schoolz.erp.identity.application;

import java.time.Clock;
import java.util.Map;
import java.util.Set;
import ma.schoolz.erp.identity.domain.IdentityTenant;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.IdentityTenantRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
@Order(10)
public class DevIdentitySeeder implements ApplicationRunner {

    private final IdentityTenantRepository tenants;
    private final UserAccountRepository accounts;
    private final SystemRoleProvisioner roleProvisioner;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final Clock clock;
    private final boolean enabled;
    private final String tenantSlug;
    private final String tenantName;
    private final String adminEmail;
    private final String adminPassword;

    public DevIdentitySeeder(
            IdentityTenantRepository tenants,
            UserAccountRepository accounts,
            SystemRoleProvisioner roleProvisioner,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            Clock clock,
            @Value("${schoolz.demo.seed-enabled:false}") boolean enabled,
            @Value("${schoolz.demo.tenant-slug:}") String tenantSlug,
            @Value("${schoolz.demo.tenant-name:}") String tenantName,
            @Value("${schoolz.demo.admin-email:}") String adminEmail,
            @Value("${schoolz.demo.admin-password:}") String adminPassword) {
        this.tenants = tenants;
        this.accounts = accounts;
        this.roleProvisioner = roleProvisioner;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.clock = clock;
        this.enabled = enabled;
        this.tenantSlug = tenantSlug;
        this.tenantName = tenantName;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (!enabled) {
            return;
        }
        passwordPolicy.validate(adminPassword, adminEmail);
        IdentityTenant tenant =
                tenants.findBySlugAndActiveTrue(IdentityTenant.normalizeSlug(tenantSlug))
                        .orElseGet(
                                () ->
                                        tenants.save(
                                                IdentityTenant.create(
                                                        tenantSlug, tenantName, clock.instant())));
        Map<String, Role> seededRoles = roleProvisioner.provision(tenant);
        if (!accounts.existsByTenantIdAndNormalizedEmail(
                tenant.id(), UserAccount.normalizeEmail(adminEmail))) {
            UserAccount admin =
                    UserAccount.create(
                            tenant.id(),
                            adminEmail,
                            "Administrateur SchoolZ",
                            passwordEncoder.encode(adminPassword),
                            "fr-MA",
                            true,
                            clock.instant());
            admin.assignRoles(
                    Set.of(seededRoles.get("SUPER_ADMIN"), seededRoles.get("ORGANIZATION_OWNER")),
                    clock.instant());
            accounts.save(admin);
        }
    }
}
