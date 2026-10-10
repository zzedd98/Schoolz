package ma.schoolz.erp.people.application;

import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.people.StudentImportOperations;
import org.springframework.stereotype.Service;

@Service
class StudentImportOperationsAdapter implements StudentImportOperations {

    private final StudentDirectoryService directory;

    StudentImportOperationsAdapter(StudentDirectoryService directory) {
        this.directory = directory;
    }

    @Override
    public UUID create(SchoolPrincipal actor, ImportedStudent student, String ipAddress) {
        StudentDirectoryService.StudentRecord created =
                directory.create(
                        actor,
                        new StudentDirectoryService.CreateStudent(
                                student.firstName(),
                                student.lastName(),
                                student.preferredName(),
                                student.birthDate(),
                                student.gender(),
                                student.nationalityCode(),
                                student.schoolIdentifier(),
                                student.householdName(),
                                student.addressLine1(),
                                student.addressLine2(),
                                student.city(),
                                student.region(),
                                student.postalCode(),
                                student.countryCode(),
                                student.guardianFirstName(),
                                student.guardianLastName(),
                                student.guardianRelationship(),
                                student.guardianEmail(),
                                student.guardianPhone(),
                                student.guardianOccupation(),
                                student.guardianNationalityCode(),
                                student.financialResponsible(),
                                student.campusId(),
                                student.academicYearId(),
                                student.classroomId(),
                                student.enrolledOn()),
                        ipAddress);
        return created.identity().id();
    }
}
