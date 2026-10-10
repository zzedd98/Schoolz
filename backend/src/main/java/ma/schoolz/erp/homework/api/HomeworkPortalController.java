package ma.schoolz.erp.homework.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.homework.application.HomeworkService;
import ma.schoolz.erp.homework.application.HomeworkService.ParentDashboard;
import ma.schoolz.erp.homework.application.HomeworkService.ParentHomework;
import ma.schoolz.erp.homework.application.HomeworkService.StudentStatusCommand;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/homework")
public class HomeworkPortalController {
    private final AdmissionPortalAccess authentication;
    private final HomeworkService homework;

    public HomeworkPortalController(
            AdmissionPortalAccess authentication, HomeworkService homework) {
        this.authentication = authentication;
        this.homework = homework;
    }

    @GetMapping
    public ParentDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) UUID studentId) {
        return homework.parentDashboard(authentication.require(rawToken), from, to, studentId);
    }

    @PatchMapping("/assignments/{id}/status")
    public ParentHomework markStatus(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID id,
            @RequestBody StudentStatusCommand command,
            HttpServletRequest request) {
        return homework.markStatus(
                authentication.require(rawToken), id, command, request.getRemoteAddr());
    }

    @GetMapping("/attachments/{id}")
    public ResponseEntity<byte[]> attachment(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID id) {
        return HomeworkController.file(
                homework.parentAttachment(authentication.require(rawToken), id));
    }
}
