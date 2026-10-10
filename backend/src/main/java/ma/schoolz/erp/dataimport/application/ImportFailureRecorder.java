package ma.schoolz.erp.dataimport.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportFailureRecorder {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ImportFailureRecorder(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID tenantId, UUID jobId) {
        jdbc.update(
                """
                UPDATE data_import_job
                SET status='FAILED',imported_rows=0,
                    error_summary='Import annulé : aucune donnée métier n''a été conservée',
                    updated_at=?,version=version+1
                WHERE id=? AND tenant_id=?
                """,
                Timestamp.from(clock.instant()),
                jobId,
                tenantId);
        jdbc.update(
                """
                UPDATE data_import_row SET status='VALID',target_record_id=NULL,updated_at=?
                WHERE job_id=? AND tenant_id=? AND status='IMPORTED'
                """,
                Timestamp.from(clock.instant()),
                jobId,
                tenantId);
    }
}
