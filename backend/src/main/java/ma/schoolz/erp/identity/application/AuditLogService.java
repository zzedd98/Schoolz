package ma.schoolz.erp.identity.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import ma.schoolz.erp.identity.IdentityAuditTrail;
import ma.schoolz.erp.identity.domain.AuditLog;
import ma.schoolz.erp.identity.persistence.AuditLogRepository;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService implements IdentityAuditTrail {

    private final AuditLogRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuditLogService(AuditLogRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(
            UUID tenantId,
            UUID actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String ipAddress,
            Map<String, ?> metadata,
            Object before,
            Object after) {
        repository.save(
                AuditLog.create(
                        tenantId,
                        actorUserId,
                        action,
                        resourceType,
                        resourceId,
                        clock.instant(),
                        ipAddress,
                        json(metadata),
                        jsonNullable(before),
                        jsonNullable(after)));
    }

    private String jsonNullable(Object value) {
        return value == null ? null : json(value);
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize audit data", exception);
        }
    }
}
