package ma.schoolz.erp.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.identity.application.UserAdministrationService;
import ma.schoolz.erp.identity.domain.AccountStatus;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class UserAdministrationController {

    private final UserAdministrationService administration;

    public UserAdministrationController(UserAdministrationService administration) {
        this.administration = administration;
    }

    @GetMapping("/users")
    public List<UserAdministrationService.UserView> users(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.listUsers(principal);
    }

    @GetMapping("/users/{accountId}")
    public UserAdministrationService.UserView user(
            @AuthenticationPrincipal SchoolPrincipal principal, @PathVariable UUID accountId) {
        return administration.getUser(principal, accountId);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedUserResponse createUser(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody CreateUserRequest request,
            HttpServletRequest servletRequest) {
        UserAdministrationService.CreatedUser created =
                administration.createUser(
                        principal,
                        new UserAdministrationService.CreateUser(
                                request.email(),
                                request.displayName(),
                                request.locale(),
                                request.roleCodes(),
                                request.staffProfileId(),
                                request.studentId()),
                        servletRequest.getRemoteAddr());
        return new CreatedUserResponse(
                created.user(), "/activate-account?token=" + created.activationToken());
    }

    @GetMapping("/users/portal-profile-options")
    public ma.schoolz.erp.identity.application.PortalProfileLinkService.Options
            portalProfileOptions(@AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.portalProfileOptions(principal);
    }

    @PatchMapping("/users/{accountId}/status")
    public UserAdministrationService.UserView changeStatus(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID accountId,
            @Valid @RequestBody ChangeStatusRequest request,
            HttpServletRequest servletRequest) {
        return administration.changeStatus(
                principal, accountId, request.status(), servletRequest.getRemoteAddr());
    }

    @PutMapping("/users/{accountId}/roles")
    public UserAdministrationService.UserView assignRoles(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID accountId,
            @Valid @RequestBody AssignRolesRequest request,
            HttpServletRequest servletRequest) {
        return administration.assignRoles(
                principal, accountId, request.roleCodes(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/roles")
    public List<UserAdministrationService.RoleView> roles(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return administration.listRoles(principal);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAdministrationService.RoleView createRole(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody CreateRoleRequest request,
            HttpServletRequest servletRequest) {
        return administration.createRole(
                principal,
                new UserAdministrationService.CreateRole(
                        request.code(), request.name(), request.permissions()),
                servletRequest.getRemoteAddr());
    }

    @GetMapping("/permissions")
    public Set<String> permissions() {
        return administration.permissionCatalog();
    }

    @GetMapping("/audit")
    public List<UserAdministrationService.AuditView> audit(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @RequestParam(defaultValue = "50") int limit) {
        return administration.listAudit(principal, limit);
    }

    public record CreateUserRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 180) String displayName,
            @NotBlank @Pattern(regexp = "fr-MA|ar-MA") String locale,
            @NotEmpty Set<@NotBlank String> roleCodes,
            UUID staffProfileId,
            UUID studentId) {}

    public record CreatedUserResponse(
            UserAdministrationService.UserView user, String activationPath) {}

    public record ChangeStatusRequest(@NotNull AccountStatus status) {}

    public record AssignRolesRequest(@NotEmpty Set<@NotBlank String> roleCodes) {}

    public record CreateRoleRequest(
            @NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 140) String name,
            @NotNull Set<@NotBlank String> permissions) {}
}
