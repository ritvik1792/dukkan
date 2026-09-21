package in.dukkan.service;

import in.dukkan.domain.ApprovalStatus;
import in.dukkan.domain.Listing;
import in.dukkan.domain.PlatformSettings;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.SettingsRepository;
import in.dukkan.repository.ShopRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShopMatchingService implements ShopMatcher {

    private final ListingRepository listings;
    private final ShopRepository shops;
    private final SettingsRepository settings;
    private final StatisticsService statistics;

    public ShopMatchingService(
            ListingRepository listings,
            ShopRepository shops,
            SettingsRepository settings,
            StatisticsService statistics) {
        this.listings = listings;
        this.shops = shops;
        this.settings = settings;
        this.statistics = statistics;
    }

    @Override
    public List<RankedCandidate> findCandidates(String catalogProductId, double buyerLat, double buyerLng) {
        PlatformSettings config = settings.findById("default").orElseThrow();
        double radiusKm = config.getDeliveryRadiusKm();
        int maxShops = Math.max(1, config.getRequestMaxShops());

        List<Listing> productListings = listings.findByCatalogProductId(catalogProductId).stream()
                .filter(listing -> listing.getStatus() == ApprovalStatus.APPROVED)
                .toList();

        List<RankedCandidate> ranked = new ArrayList<>();
        for (Listing listing : productListings) {
            Shop shop = shops.findById(listing.getShopId()).orElse(null);
            if (shop == null || shop.getStatus() != ShopStatus.ACTIVE) {
                continue;
            }
            if (!shop.isNotificationsEnabled() || !shop.isNotifyStockConfirmation()) {
                continue;
            }
            double distance = GeoDistance.haversineKm(buyerLat, buyerLng, shop.getLat(), shop.getLng());
            if (distance > radiusKm) {
                continue;
            }
            double score = rankScore(shop, distance);
            ranked.add(new RankedCandidate(shop, listing, distance, score));
        }

        ranked.sort(Comparator.comparingDouble(RankedCandidate::rankScore).reversed());
        if (ranked.size() > maxShops) {
            return ranked.subList(0, maxShops);
        }
        return ranked;
    }

    private double rankScore(Shop shop, double distanceKm) {
        double distanceScore = 1.0 / (1.0 + distanceKm);
        double ratingScore = shop.getRating() == null
                ? 0.0
                : shop.getRating().doubleValue() / 5.0;
        double history = statistics.historyScore(shop.getId());
        return (0.5 * distanceScore) + (0.3 * ratingScore) + (0.2 * history);
    }

    public static BigDecimal roundKm(double km) {
        return BigDecimal.valueOf(km).setScale(4, RoundingMode.HALF_UP);
    }

    public static BigDecimal roundScore(double score) {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }
}
