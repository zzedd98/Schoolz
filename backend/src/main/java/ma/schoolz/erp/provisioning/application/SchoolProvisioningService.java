package ma.schoolz.erp.provisioning.application;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import ma.schoolz.erp.identity.IdentityProvisioning;
import ma.schoolz.erp.organization.OrganizationProvisioning;
import ma.schoolz.erp.provisioning.domain.SchoolProvisioning;
import ma.schoolz.erp.provisioning.persistence.SchoolProvisioningRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchoolProvisioningService {

    private final SchoolProvisioningRepository provisionings;
    private final IdentityProvisioning identities;
    private final OrganizationProvisioning organizations;
    private final Clock clock;
    private final String publicBaseUrl;

    public SchoolProvisioningService(
            SchoolProvisioningRepository provisionings,
            IdentityProvisioning identities,
            OrganizationProvisioning organizations,
            Clock clock,
            @Value("${schoolz.provisioning.public-base-url:http://localhost:4200}")
                    String publicBaseUrl) {
        this.provisionings = provisionings;
        this.identities = identities;
        this.organizations = organizations;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public Result provision(Command command) {
        String fingerprint = fingerprint(command);
        SchoolProvisioning existing =
                provisionings.findByIdempotencyKey(command.idempotencyKey()).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new ProvisioningConflictException(
                        "The idempotency key is already associated with another school");
            }
            return existingResult(existing, command.tenantSlug(), command.ownerEmail());
        }

        var now = clock.instant();
        IdentityProvisioning.SchoolIdentity identity;
        try {
            identity =
                    identities.createSchoolIdentity(
                            command.tenantSlug(),
                            command.schoolName(),
                            command.ownerName(),
                            command.ownerEmail(),
                            command.locale());
        } catch (IdentityProvisioning.ProvisioningException exception) {
            throw new ProvisioningConflictException(exception.getMessage());
        }
        OrganizationProvisioning.SchoolStructure structure =
                organizations.createInitialStructure(
                        identity.tenantId(),
                        identity.tenantSlug(),
                        identity.schoolName(),
                        identity.ownerEmail(),
                        command.locale(),
                        command.campusName(),
                        command.city(),
                        command.countryCode(),
                        command.timezone());

        SchoolProvisioning provisioning =
                provisionings.save(
                        SchoolProvisioning.create(
                                command.idempotencyKey(),
                                fingerprint,
                                identity.tenantId(),
                                identity.ownerUserId(),
                                structure.organizationId(),
                                structure.campusId(),
                                now));
        return result(provisioning, identity, true);
    }

    private Result existingResult(
            SchoolProvisioning provisioning, String tenantSlug, String ownerEmail) {
        IdentityProvisioning.SchoolIdentity identity;
        try {
            identity =
                    identities.schoolIdentity(provisioning.tenantId(), provisioning.ownerUserId());
        } catch (IdentityProvisioning.ProvisioningException exception) {
            throw new ProvisioningConflictException(exception.getMessage());
        }
        if (!identity.tenantSlug().equals(normalizeSlug(tenantSlug))
                || !identity.ownerEmail().equalsIgnoreCase(ownerEmail.strip())) {
            throw new ProvisioningConflictException(
                    "The idempotency key is already associated with another school");
        }
        return result(provisioning, identity, false);
    }

    private Result result(
            SchoolProvisioning provisioning,
            IdentityProvisioning.SchoolIdentity identity,
            boolean created) {
        String activationUrl = null;
        if (identity.activationToken() != null) {
            activationUrl =
                    publicBaseUrl
                            + "/activate-account?token="
                            + URLEncoder.encode(identity.activationToken(), StandardCharsets.UTF_8)
                            + "&tenant="
                            + URLEncoder.encode(identity.tenantSlug(), StandardCharsets.UTF_8);
        }
        return new Result(
                created,
                identity.tenantId(),
                identity.tenantSlug(),
                identity.schoolName(),
                identity.ownerUserId(),
                identity.ownerEmail(),
                provisioning.organizationId(),
                provisioning.campusId(),
                identity.ownerStatus(),
                activationUrl,
                provisioning.createdAt());
    }

    private static String fingerprint(Command command) {
        try {
            String canonical =
                    String.join(
                            "\n",
                            normalizeSlug(command.tenantSlug()),
                            clean(command.schoolName()),
                            clean(command.ownerEmail()).toLowerCase(Locale.ROOT),
                            clean(command.ownerName()),
                            clean(command.locale()),
                            clean(command.campusName()),
                            clean(command.city()),
                            clean(command.countryCode()).toUpperCase(),
                            clean(command.timezone()));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to fingerprint provisioning request", exception);
        }
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private static String normalizeSlug(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    public record Command(
            String idempotencyKey,
            String tenantSlug,
            String schoolName,
            String ownerName,
            String ownerEmail,
            String locale,
            String campusName,
            String city,
            String countryCode,
            String timezone) {}

    public record Result(
            boolean created,
            UUID tenantId,
            String tenantSlug,
            String schoolName,
            UUID ownerUserId,
            String ownerEmail,
            UUID organizationId,
            UUID campusId,
            String ownerStatus,
            String activationUrl,
            java.time.Instant provisionedAt) {}
}
