package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderType;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.SellerOnboardingService;
import in.dukkan.service.SellerOnboardingService.SellerIntent;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProviderController {

    public record CreateProviderRequest(
            String name,
            String description,
            String address,
            Double lat,
            Double lng,
            List<String> categoryIds,
            ProviderType providerType,
            String profession,
            String serviceArea,
            String phone,
            String imageUrl,
            Boolean requestServices,
            Boolean requestBookings,
            Boolean requestServiceRequests) {}

    public record ProviderProfile(ShopView provider, List<in.dukkan.domain.ProviderService> services) {}

    private final ShopRepository shops;
    private final ShopViews shopViews;
    private final Access access;
    private final in.dukkan.service.ServiceCatalogService serviceCatalog;
    private final SellerOnboardingService onboarding;

    public ProviderController(
            ShopRepository shops,
            ShopViews shopViews,
            Access access,
            in.dukkan.service.ServiceCatalogService serviceCatalog,
            SellerOnboardingService onboarding) {
        this.shops = shops;
        this.shopViews = shopViews;
        this.access = access;
        this.serviceCatalog = serviceCatalog;
        this.onboarding = onboarding;
    }

    @GetMapping({"/providers", "/providers/"})
    public List<ShopView> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean servicesOnly) {
        List<Shop> all = shops.findByStatus(ShopStatus.ACTIVE);
        if (Boolean.TRUE.equals(servicesOnly)) {
            all = all.stream().filter(Shop::isServicesAllowed).toList();
        }
        if (type != null && !type.isBlank()) {
            ProviderType providerType = ProviderType.fromJson(type);
            all = all.stream().filter(s -> s.getProviderType() == providerType).toList();
        }
        return shopViews.toViews(all);
    }

    @GetMapping("/providers/{id}")
    public ProviderProfile get(@PathVariable String id) {
        Shop shop = shops.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new ProviderProfile(shopViews.toView(shop), serviceCatalog.listByProvider(id, true));
    }

    @PostMapping("/providers")
    @Transactional
    public ShopView create(Authentication auth, @RequestBody CreateProviderRequest request) {
        AppUser user = access.requireUser(auth);
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (request.address() == null || request.address().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address is required");
        }
        ProviderType type = request.providerType() == null
                ? ProviderType.SERVICE_BUSINESS
                : request.providerType();
        SellerIntent intent = new SellerIntent(
                request.name(),
                user.getName(),
                user.getEmail(),
                request.phone() != null ? request.phone() : user.getPhone(),
                request.address(),
                null,
                request.description(),
                List.of(),
                request.categoryIds(),
                true,
                type,
                request.profession(),
                request.serviceArea(),
                false,
                false,
                request.lat(),
                request.lng());
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }
        var result = onboarding.upsertProfile(user, intent);
        return shopViews.toView(result.shop());
    }
}
