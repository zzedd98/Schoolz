package ma.schoolz.erp.organization.domain;

import java.util.Locale;

public final class OrganizationCode {

    private OrganizationCode() {}

    public static String normalize(String value) {
        return value == null
                ? ""
                : value.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
    }
}
