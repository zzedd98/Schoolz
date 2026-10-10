package ma.schoolz.erp.familyportal.api;

import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.familyportal.application.FamilyPortalService;
import ma.schoolz.erp.familyportal.application.FamilyPortalService.FamilyDashboard;
import ma.schoolz.erp.familyportal.application.FamilyPortalService.PreferenceCommand;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/family-portal")
public class FamilyPortalController {
    private final AdmissionPortalAccess authentication;
    private final FamilyPortalService portal;

    public FamilyPortalController(
            AdmissionPortalAccess authentication, FamilyPortalService portal) {
        this.authentication = authentication;
        this.portal = portal;
    }

    @GetMapping
    public FamilyDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestParam(required = false) UUID studentId,
            @RequestParam(required = false) LocalDate date) {
        return portal.dashboard(authentication.require(rawToken), studentId, date);
    }

    @PutMapping("/preference")
    public FamilyDashboard preference(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody PreferenceCommand command) {
        var parent = authentication.require(rawToken);
        portal.prefer(parent, command);
        return portal.dashboard(parent, command.studentId(), null);
    }

    @GetMapping("/children/{studentId}/report-cards/{reportCardId}/pdf")
    public ResponseEntity<byte[]> reportCard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID studentId,
            @PathVariable UUID reportCardId) {
        var document = portal.reportCard(authentication.require(rawToken), studentId, reportCardId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        document.fileName(),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(document.content());
    }
}
