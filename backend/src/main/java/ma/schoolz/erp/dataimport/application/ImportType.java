package ma.schoolz.erp.dataimport.application;

import java.util.Locale;

public enum ImportType {
    STUDENTS,
    PARENTS,
    STAFF,
    CLASSES,
    PAYMENTS,
    OPENING_BALANCES;

    public static ImportType from(String value) {
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (RuntimeException exception) {
            throw new ImportValidationException("Type d'import inconnu: " + value);
        }
    }
}
