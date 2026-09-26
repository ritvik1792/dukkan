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
            Access access) {
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
    public List<ShopView> shops(Authentication auth) {
        AppUser user = access.findUser(auth).orElse(null);
        if (user == null) {
            return shopViews.toViews(shops.findByStatus(ShopStatus.ACTIVE));
        }
        if (user.getRole() == Role.ADMIN) {
            return shopViews.toViews(shops.findAll());
        }
        Map<String, Shop> merged = new LinkedHashMap<>();
        for (Shop shop : shops.findByStatus(ShopStatus.ACTIVE)) {
            merged.put(shop.getId(), shop);
        }
        for (Shop shop : shops.findByOwnerUserId(user.getId())) {
            merged.put(shop.getId(), shop);
        }
        return shopViews.toViews(new ArrayList<>(merged.values()));
    }

    @GetMapping("/shops/{id}")
    public ShopView shop(@PathVariable String id) {
        Shop shop = shops.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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
    public List<Listing> listings() {
        return listings.findAll();
    }

    @GetMapping("/listings/shop/{shopId}")
    public List<Listing> listingsByShop(@PathVariable String shopId) {
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
}
