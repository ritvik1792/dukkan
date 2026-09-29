package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.dukkan.domain.Shop;
import org.junit.jupiter.api.Test;

class ServiceabilityServiceTest {

    private final ServiceabilityService service = new ServiceabilityService();

    @Test
    void samePointIsInsideTheDefaultRadius() {
        Shop shop = shop(28.6139, 77.2090, 25);
        assertTrue(service.evaluate(shop, 28.6139, 77.2090).eligible());
    }

    @Test
    void justInsideTwentyFiveKmIsEligibleAndJustOutsideIsNot() {
        Shop shop = shop(0.2, 77.0, 25);
        double kmPerDegree = 111.32;
        double inside = 0.2 + (24.9 / kmPerDegree);
        double outside = 0.2 + (25.2 / kmPerDegree);
        assertTrue(service.evaluate(shop, inside, 77.0).eligible());
        assertFalse(service.evaluate(shop, outside, 77.0).eligible());
    }

    @Test
    void missingCustomerLocationIsNotEligible() {
        Shop shop = shop(28.6, 77.2, 25);
        assertFalse(service.evaluate(shop, null, null).eligible());
    }

    @Test
    void zeroRadiusUsesTheDefaultTwentyFiveKm() {
        Shop shop = shop(28.6, 77.2, 0);
        assertTrue(service.evaluate(shop, 28.6, 77.2).eligible());
    }

    private static Shop shop(double lat, double lng, int radius) {
        Shop shop = new Shop();
        shop.setId("shop-1");
        shop.setName("ABC Store");
        shop.setLat(lat);
        shop.setLng(lng);
        shop.setServiceRadiusKm(radius);
        return shop;
    }
}
