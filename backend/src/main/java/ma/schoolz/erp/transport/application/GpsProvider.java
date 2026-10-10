package ma.schoolz.erp.transport.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface GpsProvider {
    String key();

    Eta estimate(EtaRequest request);

    record EtaRequest(
            UUID tripId,
            BigDecimal vehicleLatitude,
            BigDecimal vehicleLongitude,
            BigDecimal stopLatitude,
            BigDecimal stopLongitude,
            Instant positionRecordedAt) {}

    record Eta(boolean available, Integer minutes, String providerKey, Instant calculatedAt) {}
}
