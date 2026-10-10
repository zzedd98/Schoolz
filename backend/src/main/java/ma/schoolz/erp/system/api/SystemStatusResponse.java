package ma.schoolz.erp.system.api;

import java.time.Instant;
import java.util.List;

public record SystemStatusResponse(
        String application,
        String status,
        Instant timestamp,
        String defaultLocale,
        List<String> supportedLocales,
        String currency,
        String timezone) {}
