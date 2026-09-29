package in.dukkan.service;

import in.dukkan.domain.Shop;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ServiceabilityService {

    public static final int DEFAULT_RADIUS_KM = 25;

    public record Verdict(
            String shopId,
            String shopName,
            boolean eligible,
            Double distanceKm,
            int radiusKm,
            String message) {}

    public int radiusKm(Shop shop) {
        int radius = shop.getServiceRadiusKm();
        return radius > 0 ? radius : DEFAULT_RADIUS_KM;
    }

    public Verdict evaluate(Shop shop, Double lat, Double lng) {
        int radius = radiusKm(shop);
        String name = shop.getName() == null || shop.getName().isBlank() ? "This seller" : shop.getName();
        if (!isPoint(lat, lng)) {
            return new Verdict(
                    shop.getId(),
                    name,
                    false,
                    null,
                    radius,
                    "Choose a delivery location before checking " + name + ".");
        }
        if (!isPoint(shop.getLat(), shop.getLng())) {
            return new Verdict(
                    shop.getId(),
                    name,
                    false,
                    null,
                    radius,
                    name + " does not deliver to this location.");
        }
        double km = GeoDistance.haversineKm(lat, lng, shop.getLat(), shop.getLng());
        boolean eligible = km <= radius;
        String message = eligible
                ? "Delivery available from " + name + "."
                : name + " does not deliver to this location.";
        return new Verdict(shop.getId(), name, eligible, round1(km), radius, message);
    }

    public void requireEligible(Shop shop, Double lat, Double lng) {
        Verdict verdict = evaluate(shop, lat, lng);
        if (!verdict.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, verdict.message());
        }
    }

    private static boolean isPoint(Double lat, Double lng) {
        return lat != null
                && lng != null
                && Double.isFinite(lat)
                && Double.isFinite(lng)
                && Math.abs(lat) <= 90
                && Math.abs(lng) <= 180
                && !(lat == 0 && lng == 0);
    }

    private static double round1(double km) {
        return Math.round(km * 10.0) / 10.0;
    }
}
