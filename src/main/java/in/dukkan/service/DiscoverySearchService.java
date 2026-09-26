package in.dukkan.service;

import in.dukkan.domain.ApprovalStatus;
import in.dukkan.domain.CatalogProduct;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.ProviderType;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.CatalogProductRepository;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.ProviderServiceRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.GeoDistance;
import in.dukkan.web.ShopViews;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DiscoverySearchService {

    public record ProductHit(
            String id,
            String name,
            String brand,
            String categoryId,
            String description,
            String imageUrl,
            String imageLabel,
            Integer imageHue,
            String type) {}

    public record ServiceHit(
            String id,
            String name,
            String description,
            String categoryId,
            BigDecimal price,
            BigDecimal startingPrice,
            Integer durationMinutes,
            String serviceArea,
            boolean bookingEnabled,
            boolean requestEnabled,
            String imageUrl,
            String providerId,
            String providerName,
            String providerProfession,
            BigDecimal providerRating,
            Double distanceKm,
            String type) {}

    public record PersonHit(
            String id,
            String name,
            String profession,
            String serviceArea,
            String description,
            String imageUrl,
            BigDecimal rating,
            BigDecimal startingPrice,
            Double distanceKm,
            List<String> serviceNames,
            String type) {}

    public record SearchResponse(
            String query,
            String filter,
            List<ProductHit> products,
            List<ShopView> shops,
            List<ServiceHit> services,
            List<PersonHit> people) {}

    private final CatalogProductRepository catalog;
    private final ListingRepository listings;
    private final ShopRepository shops;
    private final ProviderServiceRepository services;
    private final ShopViews shopViews;

    public DiscoverySearchService(
            CatalogProductRepository catalog,
            ListingRepository listings,
            ShopRepository shops,
            ProviderServiceRepository services,
            ShopViews shopViews) {
        this.catalog = catalog;
        this.listings = listings;
        this.shops = shops;
        this.services = services;
        this.shopViews = shopViews;
    }

    public SearchResponse search(String rawQuery, String filter, Double lat, Double lng, String categoryId) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        String normalizedFilter = filter == null || filter.isBlank() ? "all" : filter.trim().toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);

        List<ProductHit> products = new ArrayList<>();
        List<ShopView> shopHits = new ArrayList<>();
        List<ServiceHit> serviceHits = new ArrayList<>();
        List<PersonHit> people = new ArrayList<>();

        boolean wantProducts = normalizedFilter.equals("all") || normalizedFilter.equals("products");
        boolean wantShops = normalizedFilter.equals("all") || normalizedFilter.equals("shops");
        boolean wantServices = normalizedFilter.equals("all") || normalizedFilter.equals("services");
        boolean wantPeople = normalizedFilter.equals("all") || normalizedFilter.equals("people");

        Map<String, Double> productScores = new HashMap<>();
        if (wantProducts || !needle.isBlank()) {
            List<CatalogProduct> found = needle.isBlank()
                    ? catalog.findAll().stream().limit(40).toList()
                    : catalog.search(likeQuery(query));
            List<CatalogProduct> ranked = rankProducts(found, needle, categoryId, productScores);
            if (!wantProducts) {
                ranked = List.of();
            }
            for (CatalogProduct product : ranked) {
                products.add(new ProductHit(
                        product.getId(),
                        product.getName(),
                        product.getBrand(),
                        product.getCategoryId(),
                        product.getDescription(),
                        product.getImageUrl(),
                        product.getImageLabel(),
                        product.getImageHue(),
                        "product"));
            }
        }

        List<Shop> activeShops = shops.findByStatus(ShopStatus.ACTIVE);
        Map<String, Shop> shopById = activeShops.stream()
                .collect(Collectors.toMap(Shop::getId, s -> s, (a, b) -> a, LinkedHashMap::new));

        if (wantShops || wantPeople) {
            List<Shop> matched = needle.isBlank()
                    ? activeShops.stream()
                            .filter(s -> categoryId == null || categoryId.isBlank()
                                    || (s.getCategoryIds() != null && s.getCategoryIds().contains(categoryId)))
                            .sorted(distanceComparator(lat, lng))
                            .toList()
                    : rankShops(needle, categoryId, productScores, shopById, lat, lng);
            int shopLimit = needle.isBlank() ? 40 : 24;
            List<Shop> businessShops = matched.stream()
                    .filter(s -> s.getProviderType() != ProviderType.INDIVIDUAL)
                    .limit(shopLimit)
                    .toList();
            if (wantShops) {
                shopHits.addAll(shopViews.toViews(businessShops));
            }
            if (wantPeople) {
                List<Shop> individuals = matched.stream()
                        .filter(s -> s.getProviderType() == ProviderType.INDIVIDUAL)
                        .limit(shopLimit)
                        .toList();
                Map<String, List<ProviderService>> servicesByProvider = Map.of();
                if (!individuals.isEmpty()) {
                    servicesByProvider = services
                            .findByProviderIdInAndStatus(
                                    individuals.stream().map(Shop::getId).toList(), ServiceStatus.ACTIVE)
                            .stream()
                            .collect(Collectors.groupingBy(ProviderService::getProviderId));
                }
                for (Shop person : individuals) {
                    List<ProviderService> personServices =
                            servicesByProvider.getOrDefault(person.getId(), List.of());
                    BigDecimal starting = personServices.stream()
                            .map(s -> s.getStartingPrice() != null ? s.getStartingPrice() : s.getPrice())
                            .filter(p -> p != null)
                            .min(Comparator.naturalOrder())
                            .orElse(null);
                    people.add(new PersonHit(
                            person.getId(),
                            person.getName(),
                            person.getProfession(),
                            person.getServiceArea(),
                            person.getDescription(),
                            person.getImageUrl(),
                            person.getRating(),
                            starting,
                            distanceKm(lat, lng, person),
                            personServices.stream().map(ProviderService::getName).limit(5).toList(),
                            "person"));
                }
            }
        }

        if (wantServices) {
            List<ProviderService> foundServices = needle.isBlank()
                    ? services.findByStatus(ServiceStatus.ACTIVE).stream().limit(40).toList()
                    : rankServices(services.searchActive(likeQuery(query)), needle, categoryId);
            if (needle.isBlank() && categoryId != null && !categoryId.isBlank()) {
                foundServices = foundServices.stream()
                        .filter(s -> categoryId.equals(s.getCategoryId()))
                        .toList();
            }
            for (ProviderService service : foundServices) {
                Shop provider = shopById.get(service.getProviderId());
                if (provider == null || !provider.isServicesAllowed()) {
                    continue;
                }
                serviceHits.add(new ServiceHit(
                        service.getId(),
                        service.getName(),
                        service.getDescription(),
                        service.getCategoryId(),
                        service.getPrice(),
                        service.getStartingPrice(),
                        service.getDurationMinutes(),
                        service.getServiceArea() != null ? service.getServiceArea() : provider.getServiceArea(),
                        service.isBookingEnabled() && provider.isBookingsAllowed(),
                        service.isRequestEnabled() && provider.isServiceRequestsAllowed(),
                        service.getImageUrl() != null ? service.getImageUrl() : provider.getImageUrl(),
                        provider.getId(),
                        provider.getName(),
                        provider.getProfession(),
                        provider.getRating(),
                        distanceKm(lat, lng, provider),
                        "service"));
            }
            if (!needle.isBlank()) {
                serviceHits.sort(Comparator
                        .comparingDouble((ServiceHit hit) -> -serviceScore(needle, hit))
                        .thenComparing(hit -> hit.distanceKm() == null ? Double.MAX_VALUE : hit.distanceKm()));
            } else {
                serviceHits.sort(Comparator.comparing(
                        hit -> hit.distanceKm() == null ? Double.MAX_VALUE : hit.distanceKm()));
            }
        }

        return new SearchResponse(query, normalizedFilter, products, shopHits, serviceHits, people);
    }

    private List<CatalogProduct> rankProducts(
            List<CatalogProduct> found, String needle, String categoryId, Map<String, Double> scores) {
        if (needle.isBlank()) {
            return found;
        }
        record Row(CatalogProduct product, double score) {}
        return found.stream()
                .filter(product -> categoryId == null || categoryId.isBlank() || categoryId.equals(product.getCategoryId()))
                .map(product -> new Row(product, productScore(needle, product)))
                .filter(row -> row.score >= NameRelevance.MIN_SCORE)
                .sorted(Comparator.comparingDouble(Row::score).reversed()
                        .thenComparing(row -> row.product.getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(24)
                .peek(row -> scores.put(row.product.getId(), row.score))
                .map(Row::product)
                .toList();
    }

    private List<Shop> rankShops(
            String needle,
            String categoryId,
            Map<String, Double> productScores,
            Map<String, Shop> shopById,
            Double lat,
            Double lng) {
        Map<String, Double> scores = new HashMap<>();
        for (Shop shop : shops.searchActive(likeQuery(needle))) {
            if (shop.getStatus() != ShopStatus.ACTIVE) {
                continue;
            }
            if (categoryId != null && !categoryId.isBlank()
                    && (shop.getCategoryIds() == null || !shop.getCategoryIds().contains(categoryId))) {
                continue;
            }
            double score = shopScore(needle, shop);
            if (score >= NameRelevance.MIN_SCORE) {
                scores.merge(shop.getId(), score, Math::max);
            }
        }
        for (Map.Entry<String, Double> product : productScores.entrySet()) {
            listings.findByCatalogProductId(product.getKey()).forEach(listing -> {
                if (listing.getStatus() != ApprovalStatus.APPROVED) {
                    return;
                }
                Shop shop = shopById.get(listing.getShopId());
                if (shop == null || !shop.isProductsAllowed()) {
                    return;
                }
                scores.merge(shop.getId(), product.getValue() * 0.9, Math::max);
            });
        }
        record ScoredShop(Shop shop, double score) {}
        return scores.entrySet().stream()
                .map(entry -> {
                    Shop shop = shopById.get(entry.getKey());
                    return shop == null ? null : new ScoredShop(shop, entry.getValue());
                })
                .filter(row -> row != null)
                .sorted(Comparator
                        .comparingDouble(ScoredShop::score).reversed()
                        .thenComparing(row -> {
                            Double distance = distanceKm(lat, lng, row.shop());
                            return distance == null ? Double.MAX_VALUE : distance;
                        })
                        .thenComparing(row -> row.shop().getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(24)
                .map(ScoredShop::shop)
                .toList();
    }

    private static List<ProviderService> rankServices(
            List<ProviderService> found, String needle, String categoryId) {
        record Row(ProviderService service, double score) {}
        return found.stream()
                .filter(service -> categoryId == null || categoryId.isBlank() || categoryId.equals(service.getCategoryId()))
                .map(service -> new Row(service, NameRelevance.score(
                        needle,
                        NameRelevance.field(service.getName(), 1),
                        NameRelevance.field(service.getDescription(), 0.15),
                        NameRelevance.field(service.getServiceArea(), 0.2))))
                .filter(row -> row.score >= NameRelevance.MIN_SCORE)
                .sorted(Comparator.comparingDouble(Row::score).reversed()
                        .thenComparing(row -> row.service.getName(), String.CASE_INSENSITIVE_ORDER))
                .limit(24)
                .map(Row::service)
                .toList();
    }

    private static double productScore(String needle, CatalogProduct product) {
        return NameRelevance.score(
                needle,
                NameRelevance.field(product.getName(), 1),
                NameRelevance.field(product.getBrand(), 0.65),
                NameRelevance.field(product.getDescription(), 0.15));
    }

    private static double shopScore(String needle, Shop shop) {
        return NameRelevance.score(
                needle,
                NameRelevance.field(shop.getName(), 1),
                NameRelevance.field(shop.getProfession(), 0.8),
                NameRelevance.field(shop.getDescription(), 0.15),
                NameRelevance.field(shop.getServiceArea(), 0.2),
                NameRelevance.field(shop.getAddress(), 0.12));
    }

    private static double serviceScore(String needle, ServiceHit hit) {
        return NameRelevance.score(
                needle,
                NameRelevance.field(hit.name(), 1),
                NameRelevance.field(hit.providerName(), 0.45),
                NameRelevance.field(hit.description(), 0.15));
    }

    private static String likeQuery(String query) {
        return query.replace("%", "").replace("_", "").trim();
    }

    private static Comparator<Shop> distanceComparator(Double lat, Double lng) {
        return Comparator.comparing(shop -> {
            Double d = distanceKm(lat, lng, shop);
            return d == null ? Double.MAX_VALUE : d;
        });
    }

    private static Double distanceKm(Double lat, Double lng, Shop shop) {
        if (lat == null || lng == null) {
            return null;
        }
        return GeoDistance.haversineKm(lat, lng, shop.getLat(), shop.getLng());
    }
}
