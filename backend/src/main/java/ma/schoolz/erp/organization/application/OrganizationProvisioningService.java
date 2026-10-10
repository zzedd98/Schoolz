package ma.schoolz.erp.organization.application;

import java.time.Clock;
import java.time.LocalTime;
import java.util.UUID;
import ma.schoolz.erp.organization.OrganizationProvisioning;
import ma.schoolz.erp.organization.domain.Campus;
import ma.schoolz.erp.organization.domain.Organization;
import ma.schoolz.erp.organization.domain.SchoolSettings;
import ma.schoolz.erp.organization.persistence.CampusRepository;
import ma.schoolz.erp.organization.persistence.OrganizationRepository;
import ma.schoolz.erp.organization.persistence.SchoolSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationProvisioningService implements OrganizationProvisioning {

    private final OrganizationRepository organizations;
    private final CampusRepository campuses;
    private final SchoolSettingsRepository settings;
    private final Clock clock;

    public OrganizationProvisioningService(
            OrganizationRepository organizations,
            CampusRepository campuses,
            SchoolSettingsRepository settings,
            Clock clock) {
        this.organizations = organizations;
        this.campuses = campuses;
        this.settings = settings;
        this.clock = clock;
    }

    @Override
    @Transactional
    public SchoolStructure createInitialStructure(
            UUID tenantId,
            String tenantSlug,
            String schoolName,
            String ownerEmail,
            String locale,
            String campusName,
            String city,
            String countryCode,
            String timezone) {
        var now = clock.instant();
        Organization organization =
                organizations.save(
                        Organization.create(
                                tenantId,
                                new Organization.Details(
                                        tenantSlug,
                                        schoolName,
                                        schoolName,
                                        null,
                                        null,
                                        null,
                                        blankToNull(city),
                                        null,
                                        null,
                                        countryCode,
                                        null,
                                        ownerEmail),
                                "{}",
                                now));
        settings.save(
                SchoolSettings.create(
                        tenantId,
                        organization.id(),
                        new SchoolSettings.Values(
                                locale,
                                "fr-MA,ar-MA",
                                "MAD",
                                timezone,
                                1,
                                "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY",
                                LocalTime.of(8, 0),
                                LocalTime.of(18, 0),
                                null),
                        now));
        Campus campus =
                campuses.save(
                        Campus.create(
                                tenantId,
                                organization.id(),
                                new Campus.Details(
                                        "PRINCIPAL",
                                        campusName,
                                        null,
                                        null,
                                        blankToNull(city),
                                        null,
                                        null,
                                        countryCode,
                                        null,
                                        ownerEmail,
                                        locale,
                                        timezone),
                                now));
        return new SchoolStructure(organization.id(), campus.id());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }
}
