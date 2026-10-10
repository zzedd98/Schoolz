package ma.schoolz.erp.calendar.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CalendarSyncAdapter {
    String key();

    List<ExternalCalendarEvent> events(UUID tenantId, Instant from, Instant to);

    record ExternalCalendarEvent(
            String externalReference,
            String title,
            String description,
            String eventType,
            Instant startsAt,
            Instant endsAt,
            boolean allDay,
            String location) {}
}
