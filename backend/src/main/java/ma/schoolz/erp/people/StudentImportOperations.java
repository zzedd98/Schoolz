package ma.schoolz.erp.people;

import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;

public interface StudentImportOperations {

    UUID create(SchoolPrincipal actor, ImportedStudent student, String ipAddress);

    record ImportedStudent(
            String firstName,
            String lastName,
            String preferredName,
            LocalDate birthDate,
            String gender,
            String nationalityCode,
            String schoolIdentifier,
            String householdName,
            String addressLine1,
            String addressLine2,
            String city,
            String region,
            String postalCode,
            String countryCode,
            String guardianFirstName,
            String guardianLastName,
            String guardianRelationship,
            String guardianEmail,
            String guardianPhone,
            String guardianOccupation,
            String guardianNationalityCode,
            boolean financialResponsible,
            UUID campusId,
            UUID academicYearId,
            UUID classroomId,
            LocalDate enrolledOn) {}
}
