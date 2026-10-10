package ma.schoolz.erp.publicapi.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import ma.schoolz.erp.publicapi.application.PublicApiService;
import ma.schoolz.erp.publicapi.application.PublicApiService.ClassResource;
import ma.schoolz.erp.publicapi.application.PublicApiService.InvoiceResource;
import ma.schoolz.erp.publicapi.application.PublicApiService.PageView;
import ma.schoolz.erp.publicapi.application.PublicApiService.RequestContext;
import ma.schoolz.erp.publicapi.application.PublicApiService.StudentResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/v1")
@Tag(name = "Public API v1", description = "Stable server-to-server resources, never raw tables")
@SecurityRequirement(name = "schoolzApiKey")
public class ExternalPublicApiController {
    private final PublicApiService api;

    public ExternalPublicApiController(PublicApiService api) {
        this.api = api;
    }

    @GetMapping("/students")
    @Operation(summary = "List active student resources")
    public ResponseEntity<PageView<StudentResource>> students(
            @RequestHeader("X-SchoolZ-Api-Key") String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        long started = System.nanoTime();
        RequestContext context = authorize(key, "students:read", request);
        PageView<StudentResource> result = api.students(context, page, size);
        api.complete(context, 200, started);
        return response(context, result);
    }

    @GetMapping("/invoices")
    @Operation(summary = "List issued invoice resources")
    public ResponseEntity<PageView<InvoiceResource>> invoices(
            @RequestHeader("X-SchoolZ-Api-Key") String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        long started = System.nanoTime();
        RequestContext context = authorize(key, "invoices:read", request);
        PageView<InvoiceResource> result = api.invoices(context, page, size);
        api.complete(context, 200, started);
        return response(context, result);
    }

    @GetMapping("/classes")
    @Operation(summary = "List classroom resources with capacity and enrollment")
    public ResponseEntity<PageView<ClassResource>> classes(
            @RequestHeader("X-SchoolZ-Api-Key") String key,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        long started = System.nanoTime();
        RequestContext context = authorize(key, "classes:read", request);
        PageView<ClassResource> result = api.classes(context, page, size);
        api.complete(context, 200, started);
        return response(context, result);
    }

    private RequestContext authorize(String key, String scope, HttpServletRequest request) {
        return api.authorize(
                key, scope, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
    }

    private <T> ResponseEntity<T> response(RequestContext context, T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Request-Id", context.requestId().toString());
        headers.set("X-RateLimit-Limit", String.valueOf(context.rateLimit()));
        headers.set("X-RateLimit-Remaining", String.valueOf(context.remaining()));
        return ResponseEntity.ok().headers(headers).body(body);
    }
}
