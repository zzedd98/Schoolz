package ma.schoolz.erp.integrations.api;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import ma.schoolz.erp.integrations.application.IntegrationService;
import ma.schoolz.erp.integrations.application.IntegrationService.Dashboard;
import ma.schoolz.erp.integrations.application.IntegrationService.ExportCommand;
import ma.schoolz.erp.integrations.application.IntegrationService.FileDownload;
import ma.schoolz.erp.integrations.application.IntegrationService.TransferJobView;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/integrations")
public class IntegrationController {
    private final IntegrationService service;

    public IntegrationController(IntegrationService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal SchoolPrincipal actor) {
        return service.dashboard(actor);
    }

    @PostMapping("/{adapterKey}/exports")
    public TransferJobView export(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable String adapterKey,
            @RequestBody ExportCommand command,
            HttpServletRequest request) {
        return service.export(actor, adapterKey, command, request.getRemoteAddr());
    }

    @PostMapping(path = "/{adapterKey}/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TransferJobView importFile(
            @AuthenticationPrincipal SchoolPrincipal actor,
            @PathVariable String adapterKey,
            @RequestParam String dataset,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        return service.importFile(actor, adapterKey, dataset, file, request.getRemoteAddr());
    }

    @GetMapping("/jobs/{jobId}/file")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal SchoolPrincipal actor, @PathVariable UUID jobId) {
        FileDownload file = service.download(actor, jobId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.fileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-SHA256", file.sha256())
                .body(file.content());
    }
}
