package ma.schoolz.erp.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.foundation.security.SecureTokenService;
import ma.schoolz.erp.identity.domain.AccountStatus;
import ma.schoolz.erp.identity.domain.AuditLog;
import ma.schoolz.erp.identity.domain.PermissionCode;
import ma.schoolz.erp.identity.domain.Role;
import ma.schoolz.erp.identity.domain.UserAccount;
import ma.schoolz.erp.identity.persistence.AuditLogRepository;
import ma.schoolz.erp.identity.persistence.RefreshSessionRepository;
import ma.schoolz.erp.identity.persistence.RoleRepository;
import ma.schoolz.erp.identity.persistence.UserAccountRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserAccountRepository accounts;
    private final RoleRepository roles;
    private final AuditLogRepository auditLogs;
    private final RefreshSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationService authenticationService;
    private final PortalProfileLinkService portalLinks;
    private final SecureTokenService tokenService;
    private final AuditLogService audit;
    private final Clock clock;

    public UserAdministrationService(
            UserAccountRepository accounts,
            RoleRepository roles,
            AuditLogRepository auditLogs,
            RefreshSessionRepository sessions,
            PasswordEncoder passwordEncoder,
            AuthenticationService authenticationService,
            PortalProfileLinkService portalLinks,
            SecureTokenService tokenService,
            AuditLogService audit,
            Clock clock) {
        this.accounts = accounts;
        this.roles = roles;
        this.auditLogs = auditLogs;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.authenticationService = authenticationService;
        this.portalLinks = portalLinks;
        this.tokenService = tokenService;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('user.read')")
    public List<UserView> listUsers(SchoolPrincipal actor) {
        return accounts.findAllByTenantIdOrderByDisplayNameAsc(actor.tenantId()).stream()
                .map(UserAdministrationService::view)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('user.read')")
    public UserView getUser(SchoolPrincipal actor, UUID accountId) {
        return view(
                accounts.findByIdAndTenantId(accountId, actor.tenantId())
                        .orElseThrow(IdentityNotFoundException::new));
    }

    @Transactional
    @PreAuthorize("hasAuthority('user.write')")
    public CreatedUser createUser(SchoolPrincipal actor, CreateUser command, String ipAddress) {
        String normalizedEmail = UserAccount.normalizeEmail(command.email());
        if (accounts.existsByTenantIdAndNormalizedEmail(actor.tenantId(), normalizedEmail)) {
            throw new IdentityConflictException("An account already exists for this email");
        }
        Set<String> normalizedRoleCodes =
                command.roleCodes().stream()
                        .map(Role::normalizeCode)
                        .collect(java.util.stream.Collectors.toSet());
        Set<Role> assignedRoles =
                roles.findAllByTenantIdAndCodeIn(actor.tenantId(), normalizedRoleCodes);
        if (assignedRoles.size() != normalizedRoleCodes.size()) {
            throw new IdentityNotFoundException();
        }
        Instant now = clock.instant();
        UserAccount account =
                UserAccount.create(
                        actor.tenantId(),
                        command.email(),
                        command.displayName(),
                        passwordEncoder.encode(tokenService.generate()),
                        command.locale(),
                        false,
                        now);
        account.assignRoles(assignedRoles, now);
        accounts.saveAndFlush(account);
        portalLinks.link(
                actor.tenantId(),
                account.id(),
                normalizedRoleCodes,
                command.staffProfileId(),
                command.studentId(),
                now);
        String activationToken = authenticationService.createAccountActivationToken(account);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "identity.user_created",
                "UserAccount",
                account.id().toString(),
                ipAddress,
                Map.of("email", account.email(), "roles", normalizedRoleCodes),
                null,
                view(account));
        return new CreatedUser(view(account), activationToken);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('user.read')")
    public PortalProfileLinkService.Options portalProfileOptions(SchoolPrincipal actor) {
        return portalLinks.options(actor.tenantId());
    }

    @Transactional
    @PreAuthorize("hasAuthority('user.disable')")
    public UserView changeStatus(
            SchoolPrincipal actor, UUID accountId, AccountStatus status, String ipAddress) {
        if (actor.accountId().equals(accountId) && status != AccountStatus.ACTIVE) {
            throw new IdentityConflictException("The current account cannot disable itself");
        }
        UserAccount account =
                accounts.findByIdAndTenantId(accountId, actor.tenantId())
                        .orElseThrow(IdentityNotFoundException::new);
        AccountStatus before = account.status();
        account.changeStatus(status, clock.instant());
        if (status != AccountStatus.ACTIVE) {
            sessions.revokeAllForUser(actor.tenantId(), account.id(), clock.instant());
        }
        accounts.save(account);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "identity.user_status_changed",
                "UserAccount",
                account.id().toString(),
                ipAddress,
                Map.of(),
                Map.of("status", before),
                Map.of("status", status));
        return view(account);
    }

    @Transactional
    @PreAuthorize("hasAuthority('user.write')")
    public UserView assignRoles(
            SchoolPrincipal actor, UUID accountId, Set<String> roleCodes, String ipAddress) {
        UserAccount account =
                accounts.findByIdAndTenantId(accountId, actor.tenantId())
                        .orElseThrow(IdentityNotFoundException::new);
        Set<String> normalized =
                roleCodes.stream()
                        .map(Role::normalizeCode)
                        .collect(java.util.stream.Collectors.toSet());
        Set<Role> assigned = roles.findAllByTenantIdAndCodeIn(actor.tenantId(), normalized);
        if (assigned.size() != normalized.size()) {
            throw new IdentityNotFoundException();
        }
        Set<String> before = account.roleCodes();
        account.assignRoles(assigned, clock.instant());
        accounts.save(account);
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "identity.user_roles_changed",
                "UserAccount",
                account.id().toString(),
                ipAddress,
                Map.of(),
                Map.of("roles", before),
                Map.of("roles", account.roleCodes()));
        return view(account);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('role.read')")
    public List<RoleView> listRoles(SchoolPrincipal actor) {
        return roles.findAllByTenantIdOrderByNameAsc(actor.tenantId()).stream()
                .map(UserAdministrationService::view)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('role.write')")
    public RoleView createRole(SchoolPrincipal actor, CreateRole command, String ipAddress) {
        String code = Role.normalizeCode(command.code());
        if (roles.existsByTenantIdAndCode(actor.tenantId(), code)) {
            throw new IdentityConflictException("A role already exists with this code");
        }
        Role role =
                roles.save(
                        Role.create(
                                actor.tenantId(),
                                code,
                                command.name(),
                                false,
                                command.permissions(),
                                clock.instant()));
        audit.record(
                actor.tenantId(),
                actor.accountId(),
                "identity.role_created",
                "Role",
                role.id().toString(),
                ipAddress,
                Map.of(),
                null,
                view(role));
        return view(role);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('role.read')")
    public Set<String> permissionCatalog() {
        return PermissionCode.valuesSet();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('audit.read')")
    public List<AuditView> listAudit(SchoolPrincipal actor, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return auditLogs
                .findAllByTenantIdOrderByOccurredAtDesc(
                        actor.tenantId(), PageRequest.of(0, safeLimit))
                .stream()
                .map(UserAdministrationService::view)
                .toList();
    }

    private static UserView view(UserAccount account) {
        return new UserView(
                account.id(),
                account.email(),
                account.displayName(),
                account.locale(),
                account.status(),
                account.emailVerifiedAt(),
                account.lockedUntil(),
                account.roleCodes(),
                account.permissions(),
                account.createdAt(),
                account.updatedAt());
    }

    private static RoleView view(Role role) {
        return new RoleView(
                role.id(), role.code(), role.name(), role.systemRole(), role.permissions());
    }

    private static AuditView view(AuditLog log) {
        return new AuditView(
                log.id(),
                log.actorUserId(),
                log.action(),
                log.resourceType(),
                log.resourceId(),
                log.occurredAt(),
                log.ipAddress(),
                log.metadata());
    }

    public record CreateUser(
            String email,
            String displayName,
            String locale,
            Set<String> roleCodes,
            UUID staffProfileId,
            UUID studentId) {}

    public record CreatedUser(UserView user, String activationToken) {}

    public record CreateRole(String code, String name, Set<String> permissions) {}

    public record UserView(
            UUID id,
            String email,
            String displayName,
            String locale,
            AccountStatus status,
            Instant emailVerifiedAt,
            Instant lockedUntil,
            Set<String> roles,
            Set<String> permissions,
            Instant createdAt,
            Instant updatedAt) {}

    public record RoleView(
            UUID id, String code, String name, boolean systemRole, Set<String> permissions) {}

    public record AuditView(
            UUID id,
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            Instant occurredAt,
            String ipAddress,
            String metadata) {}
}
