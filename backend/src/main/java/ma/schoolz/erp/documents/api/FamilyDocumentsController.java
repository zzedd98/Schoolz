package ma.schoolz.erp.documents.api;

import java.util.UUID;
import ma.schoolz.erp.admissions.AdmissionPortalAccess;
import ma.schoolz.erp.documents.application.DocumentManagementService;
import ma.schoolz.erp.documents.application.DocumentManagementService.DownloadContent;
import ma.schoolz.erp.documents.application.DocumentManagementService.FamilyDocumentDashboard;
import ma.schoolz.erp.documents.application.DocumentManagementService.SignedUrlView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/documents")
public class FamilyDocumentsController {
    private final AdmissionPortalAccess authentication;
    private final DocumentManagementService documents;

    public FamilyDocumentsController(
            AdmissionPortalAccess authentication, DocumentManagementService documents) {
        this.authentication = authentication;
        this.documents = documents;
    }

    @GetMapping
    public FamilyDocumentDashboard dashboard(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken) {
        return documents.familyDashboard(authentication.require(rawToken));
    }

    @PostMapping("/{documentId}/signed-url")
    public SignedUrlView signedUrl(
            @CookieValue(name = AdmissionPortalAccess.COOKIE_NAME, required = false)
                    String rawToken,
            @PathVariable UUID documentId) {
        return documents.familySignedUrl(authentication.require(rawToken), documentId);
    }

    @GetMapping("/downloads/{token}")
    public ResponseEntity<byte[]> download(@PathVariable String token) {
        DownloadContent content = documents.download(token);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        content.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-SHA256", content.sha256())
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(
                        org.springframework.http.MediaType.parseMediaType(content.contentType()))
                .body(content.content());
    }
}
