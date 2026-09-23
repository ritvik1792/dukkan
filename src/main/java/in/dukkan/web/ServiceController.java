package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderService;
import in.dukkan.domain.ServiceStatus;
import in.dukkan.service.ServiceCatalogService;
import in.dukkan.service.ServiceCatalogService.ServiceWrite;
import java.math.BigDecimal;
import java.util.List;
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
    private final Access access;

    public ServiceController(ServiceCatalogService catalog, Access access) {
        this.catalog = catalog;
        this.access = access;
    }

    @GetMapping("/services")
    public List<ProviderService> list(@RequestParam(required = false) String providerId) {
        if (providerId != null && !providerId.isBlank()) {
            return catalog.listByProvider(providerId, true);
        }
        return catalog.listActive();
    }

    @GetMapping("/services/{id}")
    public ProviderService get(@PathVariable String id) {
        return catalog.get(id);
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
