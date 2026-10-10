package ma.schoolz.erp.provisioning.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.schoolz.erp.provisioning.application.ProvisioningAccessService;
import ma.schoolz.erp.provisioning.application.SchoolProvisioningService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/platform/provisioning")
public class SchoolProvisioningController {

    private final ProvisioningAccessService access;
    private final SchoolProvisioningService provisioning;

    public SchoolProvisioningController(
            ProvisioningAccessService access, SchoolProvisioningService provisioning) {
        this.access = access;
        this.provisioning = provisioning;
    }

    @PostMapping("/schools")
    public SchoolProvisioningService.Result provision(
            @RequestHeader(name = "X-SchoolZ-Provisioning-Key", required = false) String key,
            @Valid @RequestBody Request request) {
        if (!access.authorized(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return provisioning.provision(
                new SchoolProvisioningService.Command(
                        request.idempotencyKey(),
                        request.tenantSlug(),
                        request.schoolName(),
                        request.ownerName(),
                        request.ownerEmail(),
                        request.locale(),
                        request.campusName(),
                        request.city(),
                        request.countryCode(),
                        request.timezone()));
    }

    public record Request(
            @NotBlank @Size(max = 120) String idempotencyKey,
            @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,79}") String tenantSlug,
            @NotBlank @Size(max = 180) String schoolName,
            @NotBlank @Size(max = 180) String ownerName,
            @NotBlank @Email String ownerEmail,
            @NotBlank @Pattern(regexp = "fr-MA|ar-MA") String locale,
            @NotBlank @Size(max = 180) String campusName,
            @Size(max = 120) String city,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @NotBlank @Size(max = 80) String timezone) {}
}
