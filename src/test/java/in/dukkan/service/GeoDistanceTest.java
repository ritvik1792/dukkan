package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeoDistanceTest {

    @Test
    void haversineIsZeroForSamePoint() {
        assertEquals(0.0, GeoDistance.haversineKm(28.6139, 77.209, 28.6139, 77.209), 0.0001);
    }

    @Test
    void nearbyPointsAreWithinAFewKm() {
        double km = GeoDistance.haversineKm(28.6139, 77.2090, 28.6200, 77.2100);
        assertTrue(km > 0.5 && km < 2.0);
    }
}
