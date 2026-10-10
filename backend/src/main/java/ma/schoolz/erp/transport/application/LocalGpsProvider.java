package ma.schoolz.erp.transport.application;

import java.time.Clock;
import org.springframework.stereotype.Component;

@Component
public class LocalGpsProvider implements GpsProvider {
    private final Clock clock;

    public LocalGpsProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String key() {
        return "local-demo";
    }

    @Override
    public Eta estimate(EtaRequest request) {
        if (request.vehicleLatitude() == null
                || request.vehicleLongitude() == null
                || request.stopLatitude() == null
                || request.stopLongitude() == null) {
            return new Eta(false, null, key(), clock.instant());
        }
        double distanceKm =
                haversine(
                        request.vehicleLatitude().doubleValue(),
                        request.vehicleLongitude().doubleValue(),
                        request.stopLatitude().doubleValue(),
                        request.stopLongitude().doubleValue());
        int minutes = Math.max(1, (int) Math.ceil(distanceKm / 22.0 * 60.0));
        return new Eta(true, minutes, key(), clock.instant());
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                                * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLon / 2)
                                * Math.sin(dLon / 2);
        return earthRadiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
