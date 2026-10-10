package ma.schoolz.erp.system.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SystemStatusServiceTest {

    @Test
    void returnsMoroccanPlatformDefaultsAndClockTime() {
        Instant now = Instant.parse("2026-08-07T21:30:00Z");
        SystemStatusService service = new SystemStatusService(Clock.fixed(now, ZoneOffset.UTC));

        var status = service.currentStatus();

        assertThat(status.status()).isEqualTo("UP");
        assertThat(status.timestamp()).isEqualTo(now);
        assertThat(status.supportedLocales()).containsExactly("fr-MA", "ar-MA");
        assertThat(status.currency()).isEqualTo("MAD");
        assertThat(status.timezone()).isEqualTo("Africa/Casablanca");
    }
}
