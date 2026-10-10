package ma.schoolz.erp.people.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.people.application.StudentDirectoryService;
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
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentDirectoryService directory;

    public StudentController(StudentDirectoryService directory) {
        this.directory = directory;
    }

    @GetMapping
    public List<StudentDirectoryService.StudentSummary> students(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @RequestParam(defaultValue = "") @Size(max = 120) String search) {
        return directory.students(principal, search);
    }

    @GetMapping("/page")
    public StudentDirectoryService.StudentPage studentPage(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @RequestParam(defaultValue = "") @Size(max = 120) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "40") int size) {
        return directory.studentPage(principal, search, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDirectoryService.StudentRecord create(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @Valid @RequestBody CreateStudentRequest request,
            HttpServletRequest servletRequest) {
        return directory.create(principal, request.command(), servletRequest.getRemoteAddr());
    }

    @GetMapping("/{studentId}")
    public StudentDirectoryService.StudentRecord student(
            @AuthenticationPrincipal SchoolPrincipal principal, @PathVariable UUID studentId) {
        return directory.student(principal, studentId);
    }

    @PatchMapping("/{studentId}/archive")
    public StudentDirectoryService.StudentRecord archive(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID studentId,
            @Valid @RequestBody ArchiveRequest request,
            HttpServletRequest servletRequest) {
        return directory.archive(
                principal, studentId, request.reason(), servletRequest.getRemoteAddr());
    }

    @PutMapping("/{studentId}/photo")
    public StudentDirectoryService.StudentRecord updatePhoto(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID studentId,
            @Valid @RequestBody PhotoRequest request,
            HttpServletRequest servletRequest) {
        return directory.updatePhoto(
                principal, studentId, request.objectKey(), servletRequest.getRemoteAddr());
    }

    @PutMapping("/{studentId}/custom-fields")
    public StudentDirectoryService.StudentRecord updateCustomFields(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID studentId,
            @Valid @RequestBody CustomFieldsRequest request,
            HttpServletRequest servletRequest) {
        return directory.updateCustomFields(
                principal, studentId, request.fields(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/{studentId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public StudentDirectoryService.DocumentView addDocument(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID studentId,
            @Valid @RequestBody DocumentRequest request,
            HttpServletRequest servletRequest) {
        return directory.addDocument(
                principal, studentId, request.command(), servletRequest.getRemoteAddr());
    }

    public record CreateStudentRequest(
            @NotBlank @Size(max = 120) String studentFirstName,
            @NotBlank @Size(max = 120) String studentLastName,
            @Size(max = 120) String preferredName,
            @NotNull @Past LocalDate birthDate,
            @Size(max = 40) String gender,
            @Pattern(regexp = "[A-Za-z]{2}") String nationalityCode,
            @NotBlank @Size(max = 120) String schoolIdentifier,
            @NotBlank @Size(max = 180) String householdName,
            @NotBlank @Size(max = 220) String addressLine1,
            @Size(max = 220) String addressLine2,
            @NotBlank @Size(max = 120) String city,
            @Size(max = 120) String region,
            @Size(max = 20) String postalCode,
            @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
            @NotBlank @Size(max = 120) String guardianFirstName,
            @NotBlank @Size(max = 120) String guardianLastName,
            @NotBlank @Size(max = 50) String guardianRelationship,
            @NotBlank @Email @Size(max = 254) String guardianEmail,
            @NotBlank @Size(max = 40) String guardianPhone,
            @Size(max = 160) String guardianOccupation,
            @Pattern(regexp = "[A-Za-z]{2}") String guardianNationalityCode,
            boolean financialResponsible,
            @NotNull UUID campusId,
            @NotNull UUID academicYearId,
            UUID classroomId,
            @NotNull LocalDate enrolledOn) {
        StudentDirectoryService.CreateStudent command() {
            return new StudentDirectoryService.CreateStudent(
                    studentFirstName,
                    studentLastName,
                    preferredName,
                    birthDate,
                    gender,
                    nationalityCode,
                    schoolIdentifier,
                    householdName,
                    addressLine1,
                    addressLine2,
                    city,
                    region,
                    postalCode,
                    countryCode,
                    guardianFirstName,
                    guardianLastName,
                    guardianRelationship,
                    guardianEmail,
                    guardianPhone,
                    guardianOccupation,
                    guardianNationalityCode,
                    financialResponsible,
                    campusId,
                    academicYearId,
                    classroomId,
                    enrolledOn);
        }
    }

    public record ArchiveRequest(@NotBlank @Size(max = 300) String reason) {}

    public record PhotoRequest(@NotBlank @Size(max = 500) String objectKey) {}

    public record CustomFieldsRequest(
            @NotNull @Size(max = 30) Map<@NotBlank @Size(max = 80) String, @Size(max = 2000) String> fields) {}

    public record DocumentRequest(
            @NotBlank @Size(max = 60) String documentType,
            @NotBlank @Size(max = 180) String title,
            @NotBlank @Size(max = 500) String objectKey,
            LocalDate expiresOn) {
        StudentDirectoryService.DocumentCommand command() {
            return new StudentDirectoryService.DocumentCommand(
                    documentType, title, objectKey, expiresOn);
        }
    }
}
