package ma.schoolz.erp.system.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import ma.schoolz.erp.system.application.SystemStatusService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Public platform readiness endpoints")
class SystemStatusController {

    private final SystemStatusService statusService;

    SystemStatusController(SystemStatusService statusService) {
        this.statusService = statusService;
    }

    @GetMapping("/status")
    @Operation(summary = "Return the public platform foundation status")
    ResponseEntity<SystemStatusResponse> status() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(statusService.currentStatus());
    }
}
