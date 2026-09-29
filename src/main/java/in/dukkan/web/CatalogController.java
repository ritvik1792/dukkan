package in.dukkan.web;

import in.dukkan.domain.Advertisement;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.CatalogProduct;
import in.dukkan.domain.Category;
import in.dukkan.domain.Listing;
import in.dukkan.domain.Neighborhood;
import in.dukkan.domain.Partner;
import in.dukkan.domain.PlatformSettings;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.AdvertisementRepository;
import in.dukkan.repository.CatalogProductRepository;
import in.dukkan.repository.CategoryRepository;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.NeighborhoodRepository;
import in.dukkan.repository.PartnerRepository;
import in.dukkan.repository.SettingsRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ServiceabilityService;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CategoryRepository categories;
    private final NeighborhoodRepository neighborhoods;
    private final ShopRepository shops;
    private final CatalogProductRepository catalog;
    private final ListingRepository listings;
    private final AdvertisementRepository ads;
    private final SettingsRepository settings;
    private final PartnerRepository partners;
    private final ShopViews shopViews;
    private final Access access;
    private final ServiceabilityService serviceability;

    public CatalogController(
            CategoryRepository categories,
            NeighborhoodRepository neighborhoods,
            ShopRepository shops,
            CatalogProductRepository catalog,
            ListingRepository listings,
            AdvertisementRepository ads,
            SettingsRepository settings,
            PartnerRepository partners,
            ShopViews shopViews,
            Access access,
            ServiceabilityService serviceability) {
        this.categories = categories;
        this.neighborhoods = neighborhoods;
        this.shops = shops;
        this.catalog = catalog;
        this.listings = listings;
        this.ads = ads;
        this.settings = settings;
        this.partners = partners;
        this.shopViews = shopViews;
        this.access = access;
        this.serviceability = serviceability;
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return categories.findAll();
    }

    @GetMapping("/neighborhoods")
    public List<Neighborhood> neighborhoods() {
        return neighborhoods.findAll();
    }

    @GetMapping("/shops")
    public List<ShopView> shops(
            Authentication auth,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        AppUser user = access.findUser(auth).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) {
            return shopViews.toViews(shops.findAll());
        }
        Map<String, Shop> merged = new LinkedHashMap<>();
        for (Shop shop : shops.findByStatus(ShopStatus.ACTIVE)) {
            if (serviceability.evaluate(shop, lat, lng).eligible()) {
                merged.put(shop.getId(), shop);
            }
        }
        if (user != null) {
            for (Shop shop : shops.findByOwnerUserId(user.getId())) {
                merged.put(shop.getId(), shop);
            }
        }
        return shopViews.toViews(new ArrayList<>(merged.values()));
    }

    @GetMapping("/shops/{id}")
    public ShopView shop(
            Authentication auth,
            @PathVariable String id,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        Shop shop = shops.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertServiceable(auth, shop, lat, lng);
        return shopViews.toView(shop);
    }

    public record SettingsPatch(
            Integer deliveryRadiusKm,
            Integer partnerEtaMinutes,
            Boolean showDemoRoleSwitcher,
            Integer requestResponseWindowSeconds,
            Integer requestWaveSize,
            Integer requestMaxShops,
            Integer offerExpirySeconds,
            Integer requestMaxWaves,
            Boolean quickDeliveryEnabled) {}

    @PatchMapping("/settings")
    @Transactional
    public PlatformSettings patchSettings(Authentication auth, @RequestBody SettingsPatch request) {
        access.requireAdmin(auth);
        PlatformSettings config = settings.findById("default").orElseThrow();
        if (request.deliveryRadiusKm() != null && request.deliveryRadiusKm() > 0) {
            config.setDeliveryRadiusKm(request.deliveryRadiusKm());
        }
        if (request.partnerEtaMinutes() != null && request.partnerEtaMinutes() > 0) {
            config.setPartnerEtaMinutes(request.partnerEtaMinutes());
        }
        if (request.showDemoRoleSwitcher() != null) {
            config.setShowDemoRoleSwitcher(request.showDemoRoleSwitcher());
        }
        if (request.requestResponseWindowSeconds() != null && request.requestResponseWindowSeconds() > 0) {
            config.setRequestResponseWindowSeconds(request.requestResponseWindowSeconds());
        }
        if (request.requestWaveSize() != null && request.requestWaveSize() > 0) {
            config.setRequestWaveSize(request.requestWaveSize());
        }
        if (request.requestMaxShops() != null && request.requestMaxShops() > 0) {
            config.setRequestMaxShops(request.requestMaxShops());
        }
        if (request.offerExpirySeconds() != null && request.offerExpirySeconds() > 0) {
            config.setOfferExpirySeconds(request.offerExpirySeconds());
        }
        if (request.requestMaxWaves() != null && request.requestMaxWaves() > 0) {
            config.setRequestMaxWaves(request.requestMaxWaves());
        }
        if (request.quickDeliveryEnabled() != null) {
            config.setQuickDeliveryEnabled(request.quickDeliveryEnabled());
        }
        return settings.save(config);
    }

    @GetMapping("/catalog")
    public List<CatalogProduct> catalog() {
        return catalog.findAll();
    }

    @GetMapping("/catalog/{id}")
    public CatalogProduct product(@PathVariable String id) {
        return catalog.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/listings")
    public List<Listing> listings(
            Authentication auth,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        AppUser user = access.findUser(auth).orElse(null);
        if (user != null && user.getRole() == Role.ADMIN) {
            return listings.findAll();
        }
        java.util.Set<String> owned = ownedShopIds(user);
        List<Listing> visible = new ArrayList<>();
        for (Listing listing : listings.findAll()) {
            if (owned.contains(listing.getShopId())) {
                visible.add(listing);
                continue;
            }
            Shop shop = shops.findById(listing.getShopId()).orElse(null);
            if (shop != null
                    && shop.getStatus() == ShopStatus.ACTIVE
                    && listing.getStatus() == in.dukkan.domain.ApprovalStatus.APPROVED
                    && serviceability.evaluate(shop, lat, lng).eligible()) {
                visible.add(listing);
            }
        }
        return visible;
    }

    @GetMapping("/listings/shop/{shopId}")
    public List<Listing> listingsByShop(
            Authentication auth,
            @PathVariable String shopId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        Shop shop = shops.findById(shopId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertServiceable(auth, shop, lat, lng);
        return listings.findByShopId(shopId);
    }

    @GetMapping("/ads")
    public List<Advertisement> ads() {
        return ads.findByActiveTrue();
    }

    @GetMapping("/settings")
    public PlatformSettings settings() {
        return settings.findById("default").orElseThrow();
    }

    @GetMapping("/partners")
    public List<Partner> partners() {
        return partners.findAll();
    }

    private void assertServiceable(Authentication auth, Shop shop, Double lat, Double lng) {
        AppUser user = access.findUser(auth).orElse(null);
        if (user != null && (user.getRole() == Role.ADMIN || shop.getOwnerUserId().equals(user.getId()))) {
            return;
        }
        var verdict = serviceability.evaluate(shop, lat, lng);
        if (!verdict.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, verdict.message());
        }
    }

    private java.util.Set<String> ownedShopIds(AppUser user) {
        if (user == null || user.getRole() != Role.SELLER) {
            return java.util.Set.of();
        }
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (Shop shop : shops.findByOwnerUserId(user.getId())) {
            ids.add(shop.getId());
        }
        if (user.getShopId() != null) {
            ids.add(user.getShopId());
        }
        return ids;
    }
}
