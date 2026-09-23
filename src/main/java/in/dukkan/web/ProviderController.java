package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ProviderType;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.domain.VerificationStatus;
import in.dukkan.repository.NeighborhoodRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.UserRepository;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.math.BigDecimal;
import java.util.HashSet;
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
    private final UserRepository users;
    private final NeighborhoodRepository neighborhoods;
    private final ShopViews shopViews;
    private final Access access;
    private final in.dukkan.service.ServiceCatalogService serviceCatalog;

    public ProviderController(
            ShopRepository shops,
            UserRepository users,
            NeighborhoodRepository neighborhoods,
            ShopViews shopViews,
            Access access,
            in.dukkan.service.ServiceCatalogService serviceCatalog) {
        this.shops = shops;
        this.users = users;
        this.neighborhoods = neighborhoods;
        this.shopViews = shopViews;
        this.access = access;
        this.serviceCatalog = serviceCatalog;
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
        var neighborhood = neighborhoods.findAll().stream().findFirst();
        double lat = request.lat() != null
                ? request.lat()
                : neighborhood.map(item -> item.getLat()).orElse(28.6328);
        double lng = request.lng() != null
                ? request.lng()
                : neighborhood.map(item -> item.getLng()).orElse(77.2197);

        Shop shop = new Shop();
        shop.setId(Ids.next(type == ProviderType.INDIVIDUAL ? "prv" : "shop"));
        shop.setName(request.name().trim());
        shop.setOwnerUserId(user.getId());
        shop.setDescription(blankToNull(request.description()));
        shop.setAddress(request.address().trim());
        shop.setLat(lat);
        shop.setLng(lng);
        shop.setRating(BigDecimal.ZERO);
        shop.setReviewCount(0);
        shop.setVerified(false);
        shop.setYearStarted(java.time.Year.now().getValue());
        shop.setStatus(ShopStatus.PENDING);
        shop.setPartnerDeliveryEnabled(false);
        shop.setShopDeliveryEnabled(false);
        shop.setPartnerDeliveryFee(BigDecimal.ZERO);
        shop.setShopDeliveryFee(BigDecimal.ZERO);
        shop.setMinOrderAmount(BigDecimal.ZERO);
        shop.setOpen(true);
        shop.setOpenTime("09:00");
        shop.setCloseTime("21:00");
        shop.setProviderType(type);
        shop.setProductsAllowed(type == ProviderType.PRODUCT_BUSINESS);
        shop.setOrdersAllowed(type == ProviderType.PRODUCT_BUSINESS);
        // Service capabilities require admin enablement (serviceAllowed), but applicants can request intent.
        shop.setServicesAllowed(false);
        shop.setBookingsAllowed(false);
        shop.setServiceRequestsAllowed(false);
        shop.setQuickDeliveryAllowed(false);
        shop.setVerificationStatus(VerificationStatus.UNVERIFIED);
        shop.setProfession(blankToNull(request.profession()));
        shop.setServiceArea(blankToNull(request.serviceArea()));
        shop.setImageUrl(blankToNull(request.imageUrl()));
        if (request.categoryIds() != null) {
            shop.setCategoryIds(new HashSet<>(request.categoryIds()));
        }
        shops.save(shop);

        if (user.getRole() != Role.ADMIN) {
            user.setRole(Role.SELLER);
        }
        if (user.getShopId() == null || user.getShopId().isBlank()) {
            user.setShopId(shop.getId());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }
        users.save(user);
        return shopViews.toView(shop);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
