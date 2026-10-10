package ma.schoolz.erp.admissions.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.UUID;
import ma.schoolz.erp.admissions.application.AdmissionPortalService;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ApplicantCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ApplicantView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ApplicationCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ApplicationView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.DocumentContent;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.DocumentView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.FormCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.NotificationView;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.PaymentCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.PortalHome;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.ProspectCommand;
import ma.schoolz.erp.admissions.application.AdmissionPortalService.PublicCatalog;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService.LoginCommand;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService.PortalPrincipal;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService.PortalSession;
import ma.schoolz.erp.admissions.application.PortalAuthenticationService.RegisterCommand;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/public/admissions")
public class AdmissionPortalController {

    private final PortalAuthenticationService authentication;
    private final AdmissionPortalService portal;
    private final boolean secureCookies;

    public AdmissionPortalController(
            PortalAuthenticationService authentication,
            AdmissionPortalService portal,
            @Value("${schoolz.security.secure-cookies:true}") boolean secureCookies) {
        this.authentication = authentication;
        this.portal = portal;
        this.secureCookies = secureCookies;
    }

    @GetMapping("/{tenantSlug}/catalog")
    public PublicCatalog catalog(@PathVariable String tenantSlug) {
        return portal.catalog(tenantSlug);
    }

    @PostMapping("/{tenantSlug}/prospects")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public IdResponse prospect(
            @PathVariable String tenantSlug, @RequestBody ProspectCommand command) {
        return new IdResponse(portal.createPublicProspect(tenantSlug, command));
    }

    @PostMapping("/accounts")
    public ResponseEntity<PortalPrincipal> register(
            @RequestBody RegisterCommand command, HttpServletRequest request) {
        PortalSession session =
                authentication.register(
                        command,
                        request.getRemoteAddr(),
                        request.getHeader(HttpHeaders.USER_AGENT));
        return sessionResponse(session);
    }

    @PostMapping("/sessions")
    public ResponseEntity<PortalPrincipal> login(
            @RequestBody LoginCommand command, HttpServletRequest request) {
        PortalSession session =
                authentication.login(
                        command,
                        request.getRemoteAddr(),
                        request.getHeader(HttpHeaders.USER_AGENT));
        return sessionResponse(session);
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> logout(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken) {
        authentication.logout(rawToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public PortalHome home(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken) {
        return portal.home(authentication.require(rawToken));
    }

    @PostMapping("/applicants")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApplicantView applicant(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody ApplicantCommand command) {
        return portal.createApplicant(authentication.require(rawToken), command);
    }

    @GetMapping("/applications")
    public java.util.List<ApplicationView> applications(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken) {
        return portal.applications(authentication.require(rawToken));
    }

    @PostMapping("/applications")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public ApplicationView application(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @RequestBody ApplicationCommand command) {
        return portal.createApplication(authentication.require(rawToken), command);
    }

    @PutMapping("/applications/{applicationId}")
    public ApplicationView updateApplication(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID applicationId,
            @RequestBody FormCommand command) {
        return portal.updateApplication(authentication.require(rawToken), applicationId, command);
    }

    @PostMapping(
            value = "/applications/{applicationId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public DocumentView document(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID applicationId,
            @RequestParam String documentType,
            @RequestPart MultipartFile file) {
        return portal.uploadDocument(
                authentication.require(rawToken), applicationId, documentType, file);
    }

    @GetMapping("/documents/{documentId}")
    public ResponseEntity<byte[]> document(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID documentId) {
        DocumentContent document = portal.document(authentication.require(rawToken), documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .contentLength(document.bytes().length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(document.fileName())
                                .build()
                                .toString())
                .body(document.bytes());
    }

    @PostMapping("/applications/{applicationId}/submit")
    public ApplicationView submit(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID applicationId) {
        return portal.submit(authentication.require(rawToken), applicationId);
    }

    @PostMapping("/applications/{applicationId}/fee-payments")
    public ApplicationView pay(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID applicationId,
            @RequestBody PaymentCommand command) {
        return portal.payFee(authentication.require(rawToken), applicationId, command);
    }

    @PostMapping("/offers/{offerId}/accept")
    public ApplicationView acceptOffer(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID offerId) {
        return portal.acceptOffer(authentication.require(rawToken), offerId);
    }

    @GetMapping("/notifications")
    public java.util.List<NotificationView> notifications(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken) {
        return portal.notifications(authentication.require(rawToken));
    }

    @PostMapping("/notifications/{notificationId}/read")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void readNotification(
            @CookieValue(name = PortalAuthenticationService.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID notificationId) {
        portal.markNotificationRead(authentication.require(rawToken), notificationId);
    }

    private ResponseEntity<PortalPrincipal> sessionResponse(PortalSession session) {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.SET_COOKIE,
                        cookie(session.rawToken(), authentication.sessionTtl()).toString())
                .body(session.principal());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(PortalAuthenticationService.COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite("Strict")
                .path("/api/v1/public")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie expiredCookie() {
        return cookie("", Duration.ZERO);
    }

    public record IdResponse(UUID id) {}
}
