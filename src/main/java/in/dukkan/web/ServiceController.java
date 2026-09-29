package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.domain.Shop;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ServiceCatalogService;
import in.dukkan.service.ServiceabilityService;
import in.dukkan.service.ServiceCatalogService.ServiceWrite;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ServiceController {

    public record ServiceBody(
            String name,
            String description,
            String categoryId,
            BigDecimal price,
            BigDecimal startingPrice,
            Integer durationMinutes,
            String serviceArea,
            Boolean bookingEnabled,
            Boolean requestEnabled,
            String imageUrl,
            List<String> imageUrls,
            ServiceStatus status) {}

    private final ServiceCatalogService catalog;
    private final ShopRepository shops;
    private final ServiceabilityService serviceability;
    private final Access access;

    public ServiceController(
            ServiceCatalogService catalog,
            ShopRepository shops,
            ServiceabilityService serviceability,
            Access access) {
        this.catalog = catalog;
        this.shops = shops;
        this.serviceability = serviceability;
        this.access = access;
    }

    @GetMapping("/services")
    public List<ProviderService> list(
            Authentication auth,
            @RequestParam(required = false) String providerId,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        if (providerId != null && !providerId.isBlank()) {
            Shop shop = shops.findById(providerId).orElse(null);
            if (shop != null) {
                assertServiceable(auth, shop, lat, lng);
            }
            return catalog.listByProvider(providerId, true);
        }
        return catalog.listActive().stream()
                .filter(service -> {
                    Shop shop = shops.findById(service.getProviderId()).orElse(null);
                    return shop != null && serviceability.evaluate(shop, lat, lng).eligible();
                })
                .toList();
    }

    @GetMapping("/services/{id}")
    public ProviderService get(
            Authentication auth,
            @PathVariable String id,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng) {
        ProviderService service = catalog.get(id);
        Shop shop = shops.findById(service.getProviderId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Provider not found"));
        assertServiceable(auth, shop, lat, lng);
        return service;
    }

    private void assertServiceable(Authentication auth, Shop shop, Double lat, Double lng) {
        AppUser user = access.findUser(auth).orElse(null);
        if (user != null && (access.isAdmin(user) || shop.getOwnerUserId().equals(user.getId()))) {
            return;
        }
        var verdict = serviceability.evaluate(shop, lat, lng);
        if (!verdict.eligible()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, verdict.message());
        }
    }

    @GetMapping({"/seller/services", "/provider/services"})
    public List<ProviderService> sellerList(
            Authentication auth, @RequestParam(required = false) String providerId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        String id = resolveProviderId(user, providerId);
        return catalog.listByProvider(id, false);
    }

    @PostMapping({"/seller/services", "/provider/services"})
    public ProviderService create(
            Authentication auth,
            @RequestBody ServiceBody body,
            @RequestParam(required = false) String providerId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        String id = resolveProviderId(user, providerId);
        return catalog.create(user, id, toWrite(body));
    }

    @PatchMapping({"/seller/services/{id}", "/provider/services/{id}"})
    public ProviderService update(
            Authentication auth, @PathVariable String id, @RequestBody ServiceBody body) {
        AppUser user = access.requireSellerOrAdmin(auth);
        return catalog.update(user, id, toWrite(body));
    }

    @DeleteMapping({"/seller/services/{id}", "/provider/services/{id}"})
    public void delete(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireSellerOrAdmin(auth);
        catalog.delete(user, id);
    }

    private String resolveProviderId(AppUser user, String providerId) {
        if (providerId != null && !providerId.isBlank()) {
            return providerId;
        }
        if (user.getShopId() == null || user.getShopId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Provider id is required");
        }
        return user.getShopId();
    }

    private static ServiceWrite toWrite(ServiceBody body) {
        return new ServiceWrite(
                body.name(),
                body.description(),
                body.categoryId(),
                body.price(),
                body.startingPrice(),
                body.durationMinutes(),
                body.serviceArea(),
                body.bookingEnabled(),
                body.requestEnabled(),
                body.imageUrl(),
                body.imageUrls(),
                body.status());
    }
}
