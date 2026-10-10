package ma.schoolz.erp.documents.api;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;
import ma.schoolz.erp.documents.application.DocumentManagementService;
import ma.schoolz.erp.documents.application.DocumentManagementService.CategoryCommand;
import ma.schoolz.erp.documents.application.DocumentManagementService.CategoryView;
import ma.schoolz.erp.documents.application.DocumentManagementService.DocumentDashboard;
import ma.schoolz.erp.documents.application.DocumentManagementService.DocumentView;
import ma.schoolz.erp.documents.application.DocumentManagementService.GeneratePdfCommand;
import ma.schoolz.erp.documents.application.DocumentManagementService.ShareCommand;
import ma.schoolz.erp.documents.application.DocumentManagementService.SignedUrlView;
import ma.schoolz.erp.documents.application.DocumentManagementService.UploadMetadata;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentsController {
    private final DocumentManagementService documents;

    public DocumentsController(DocumentManagementService documents) {
        this.documents = documents;
    }

    @GetMapping("/dashboard")
    public DocumentDashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return documents.dashboard(actor);
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView category(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody CategoryCommand command,
            HttpServletRequest request) {
        return documents.createCategory(actor, command, request.getRemoteAddr());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView upload(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestParam UUID categoryId,
            @RequestParam String documentType,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam String ownerKind,
            @RequestParam(required = false) UUID ownerReferenceId,
            @RequestParam(defaultValue = "INTERNAL") String visibility,
            @RequestParam(required = false) LocalDate expiresOn,
            @RequestParam MultipartFile file,
            HttpServletRequest request)
            throws IOException {
        return documents.upload(
                actor,
                new UploadMetadata(
                        categoryId,
                        documentType,
                        title,
                        description,
                        ownerKind,
                        ownerReferenceId,
                        visibility,
                        expiresOn),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                request.getRemoteAddr());
    }

    @PostMapping("/{documentId}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView version(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID documentId,
            @RequestParam MultipartFile file,
            HttpServletRequest request)
            throws IOException {
        return documents.addVersion(
                actor,
                documentId,
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes(),
                request.getRemoteAddr());
    }

    @PostMapping("/generated")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentView generate(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @RequestBody GeneratePdfCommand command,
            HttpServletRequest request) {
        return documents.generatePdf(actor, command, request.getRemoteAddr());
    }

    @PutMapping("/{documentId}/family-share")
    public DocumentView share(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable UUID documentId,
            @RequestBody ShareCommand command,
            HttpServletRequest request) {
        return documents.shareWithFamily(actor, documentId, command, request.getRemoteAddr());
    }

    @PostMapping("/{documentId}/signed-url")
    public SignedUrlView signedUrl(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID documentId) {
        return documents.signedUrl(actor, documentId);
    }
}
