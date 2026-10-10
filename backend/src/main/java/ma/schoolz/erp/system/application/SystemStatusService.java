package ma.schoolz.erp.system.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import ma.schoolz.erp.system.api.SystemStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class SystemStatusService {

    private final Clock clock;

    public SystemStatusService(Clock clock) {
        this.clock = clock;
    }

    public SystemStatusResponse currentStatus() {
        return new SystemStatusResponse(
                "SchoolZ ERP",
                "UP",
                Instant.now(clock),
                "fr-MA",
                List.of("fr-MA", "ar-MA"),
                "MAD",
                "Africa/Casablanca");
    }
}
