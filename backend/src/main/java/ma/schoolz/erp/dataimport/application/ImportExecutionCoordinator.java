package ma.schoolz.erp.dataimport.application;

import java.util.UUID;
import ma.schoolz.erp.dataimport.application.ImportWorkflowService.ImportJobView;
import ma.schoolz.erp.foundation.security.SchoolPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class ImportExecutionCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportExecutionCoordinator.class);

    private final ImportExecutionService execution;
    private final ImportFailureRecorder failures;
    private final ImportWorkflowService workflow;

    public ImportExecutionCoordinator(
            ImportExecutionService execution,
            ImportFailureRecorder failures,
            ImportWorkflowService workflow) {
        this.execution = execution;
        this.failures = failures;
        this.workflow = workflow;
    }

    @PreAuthorize("hasAuthority('data-import.write')")
    public ImportJobView execute(SchoolPrincipal actor, UUID jobId, String ipAddress) {
        try {
            execution.execute(actor, jobId, ipAddress);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Atomic data import {} was rolled back after {}",
                    jobId,
                    exception.getClass().getSimpleName());
            failures.record(actor.tenantId(), jobId);
        }
        return workflow.job(actor, jobId);
    }
}
