package ma.schoolz.erp.admissions.application;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public enum AdmissionStage {
    NEW_LEAD,
    CONTACTED,
    SCHOOL_VISIT,
    APPLICATION_STARTED,
    APPLICATION_COMPLETE,
    TEST_SCHEDULED,
    REVIEW,
    ACCEPTED,
    WAITLISTED,
    REJECTED,
    ENROLLED;

    private static final Map<AdmissionStage, Set<AdmissionStage>> TRANSITIONS =
            Map.ofEntries(
                    Map.entry(NEW_LEAD, EnumSet.of(CONTACTED)),
                    Map.entry(CONTACTED, EnumSet.of(SCHOOL_VISIT, APPLICATION_STARTED)),
                    Map.entry(SCHOOL_VISIT, EnumSet.of(APPLICATION_STARTED)),
                    Map.entry(APPLICATION_STARTED, EnumSet.of(APPLICATION_COMPLETE)),
                    Map.entry(APPLICATION_COMPLETE, EnumSet.of(TEST_SCHEDULED, REVIEW)),
                    Map.entry(TEST_SCHEDULED, EnumSet.of(REVIEW)),
                    Map.entry(REVIEW, EnumSet.of(ACCEPTED, WAITLISTED, REJECTED)),
                    Map.entry(WAITLISTED, EnumSet.of(ACCEPTED, REJECTED)),
                    Map.entry(ACCEPTED, EnumSet.of(ENROLLED)));

    public boolean canTransitionTo(AdmissionStage target) {
        return this == target || TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public static AdmissionStage from(String value) {
        try {
            return valueOf(value == null ? "" : value.strip().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new AdmissionValidationException("Statut d'admission invalide");
        }
    }
}
