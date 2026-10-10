package ma.schoolz.erp.publicapi.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.publicapi.application.PublicApiService;
import ma.schoolz.erp.publicapi.application.PublicApiService.ApiKeyCommand;
import ma.schoolz.erp.publicapi.application.PublicApiService.ClientView;
import ma.schoolz.erp.publicapi.application.PublicApiService.CreatedKey;
import ma.schoolz.erp.publicapi.application.PublicApiService.Dashboard;
import ma.schoolz.erp.publicapi.application.PublicApiService.RevokeCommand;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/api-access")
public class PublicApiManagementController {
    private final PublicApiService api;

    public PublicApiManagementController(PublicApiService api) {
        this.api = api;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return api.dashboard(actor);
    }

    @PostMapping("/clients")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedKey create(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody ApiKeyCommand command,
            HttpServletRequest request) {
        return api.create(actor, command, request.getRemoteAddr());
    }

    @PostMapping("/clients/{clientId}/revoke")
    public ClientView revoke(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID clientId,
            @RequestBody RevokeCommand command,
            HttpServletRequest request) {
        return api.revoke(actor, clientId, command, request.getRemoteAddr());
    }
}
