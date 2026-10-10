package ma.schoolz.erp.dataimport.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.dataimport.application.ImportExecutionCoordinator;
import ma.schoolz.erp.dataimport.application.ImportType;
import ma.schoolz.erp.dataimport.application.ImportWorkflowService;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/api/v1/imports")
public class DataImportController {

    private final ImportWorkflowService workflow;
    private final ImportExecutionCoordinator execution;

    public DataImportController(
            ImportWorkflowService workflow, ImportExecutionCoordinator execution) {
        this.workflow = workflow;
        this.execution = execution;
    }

    @GetMapping
    public List<ImportWorkflowService.ImportJobView> jobs(
            @AuthenticationPrincipal SchoolPrincipal principal) {
        return workflow.jobs(principal);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportWorkflowService.ImportJobView upload(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @RequestParam String type,
            @RequestPart MultipartFile file,
            HttpServletRequest request) {
        return workflow.upload(principal, ImportType.from(type), file, request.getRemoteAddr());
    }

    @GetMapping("/{jobId}")
    public ImportWorkflowService.ImportJobView job(
            @AuthenticationPrincipal SchoolPrincipal principal, @PathVariable UUID jobId) {
        return workflow.job(principal, jobId);
    }

    @PutMapping("/{jobId}/mapping")
    public ImportWorkflowService.ImportJobView mapping(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID jobId,
            @Valid @RequestBody MappingRequest request,
            HttpServletRequest servletRequest) {
        return workflow.map(principal, jobId, request.mapping(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/{jobId}/validate")
    public ImportWorkflowService.ImportPreview validate(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID jobId,
            HttpServletRequest request) {
        return workflow.validate(principal, jobId, request.getRemoteAddr());
    }

    @GetMapping("/{jobId}/preview")
    public ImportWorkflowService.ImportPreview preview(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "100") int limit) {
        return workflow.preview(principal, jobId, limit);
    }

    @PostMapping("/{jobId}/execute")
    public ImportWorkflowService.ImportJobView execute(
            @AuthenticationPrincipal SchoolPrincipal principal,
            @PathVariable UUID jobId,
            HttpServletRequest request) {
        return execution.execute(principal, jobId, request.getRemoteAddr());
    }

    @GetMapping("/templates/{type}.xlsx")
    public ResponseEntity<Resource> template(@PathVariable String type) throws IOException {
        ImportType importType = ImportType.from(type);
        String fileName =
                "schoolz-import-" + importType.name().toLowerCase().replace('_', '-') + ".xlsx";
        ClassPathResource resource = new ClassPathResource("import-templates/" + fileName);
        if (!resource.exists()) {
            throw new IOException("Import template unavailable");
        }
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(resource.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(resource);
    }

    public record MappingRequest(
            @NotNull @NotEmpty @Size(max = 40) Map<@Size(max = 80) String, @Size(max = 255) String> mapping) {}
}
