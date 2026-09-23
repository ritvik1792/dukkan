package in.dukkan.service;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

        boolean serviceIntent = looksLikeServiceQuery(needle);
        boolean productIntent = !serviceIntent && !needle.isBlank();

        if (wantProducts && (needle.isBlank() || productIntent || !serviceIntent)) {
            List<CatalogProduct> found = needle.isBlank()
                    ? catalog.findAll().stream().limit(40).toList()
                    : catalog.search(query);
            for (CatalogProduct product : found) {
                if (categoryId != null && !categoryId.isBlank() && !categoryId.equals(product.getCategoryId())) {
                    continue;
                }
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
                    ? activeShops
                    : shops.searchActive(query).stream()
                            .filter(s -> s.getStatus() == ShopStatus.ACTIVE)
                            .toList();
            if (categoryId != null && !categoryId.isBlank()) {
                matched = matched.stream()
                        .filter(s -> s.getCategoryIds() != null && s.getCategoryIds().contains(categoryId))
                        .toList();
            }
            List<Shop> businessShops = matched.stream()
                    .filter(s -> s.getProviderType() != ProviderType.INDIVIDUAL)
                    .sorted(distanceComparator(lat, lng))
                    .limit(40)
                    .toList();
            if (wantShops) {
                shopHits.addAll(shopViews.toViews(businessShops));
            }
            if (wantPeople) {
                List<Shop> individuals = matched.stream()
                        .filter(s -> s.getProviderType() == ProviderType.INDIVIDUAL)
                        .sorted(distanceComparator(lat, lng))
                        .limit(40)
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
                    : services.searchActive(query);
            if (categoryId != null && !categoryId.isBlank()) {
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
            serviceHits.sort(Comparator.comparing(
                    hit -> hit.distanceKm() == null ? Double.MAX_VALUE : hit.distanceKm()));
        }

        // When the query looks like a service, de-emphasize empty product noise by keeping products
        // only if they actually matched; already handled by productIntent.
        if (serviceIntent && wantProducts && products.isEmpty() && !needle.isBlank()) {
            // no-op: products stay empty for service-like queries without catalog matches
        }

        // Promote shops that sell matching products into shop results.
        if (wantShops && !products.isEmpty()) {
            Set<String> productIds = products.stream().map(ProductHit::id).collect(Collectors.toSet());
            Set<String> already = shopHits.stream().map(ShopView::id).collect(Collectors.toSet());
            List<Shop> extra = new ArrayList<>();
            for (String productId : productIds) {
                listings.findByCatalogProductId(productId).forEach(listing -> {
                    Shop shop = shopById.get(listing.getShopId());
                    if (shop != null && shop.isProductsAllowed() && already.add(shop.getId())) {
                        extra.add(shop);
                    }
                });
            }
            if (!extra.isEmpty()) {
                List<ShopView> extraViews = shopViews.toViews(
                        extra.stream().sorted(distanceComparator(lat, lng)).limit(20).toList());
                shopHits.addAll(0, extraViews);
            }
        }

        return new SearchResponse(query, normalizedFilter, products, shopHits, serviceHits, people);
    }

    private static boolean looksLikeServiceQuery(String needle) {
        if (needle.isBlank()) {
            return false;
        }
        return needle.contains("repair")
                || needle.contains("salon")
                || needle.contains("electrician")
                || needle.contains("plumber")
                || needle.contains("tutor")
                || needle.contains("beauty")
                || needle.contains("fitness")
                || needle.contains("ac ")
                || needle.startsWith("ac")
                || needle.contains("service")
                || needle.contains("booking");
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
