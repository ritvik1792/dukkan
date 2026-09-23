package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ApplicationStatus;
import in.dukkan.domain.Role;
import in.dukkan.domain.SellerApplication;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ApplicationRepository;
import in.dukkan.repository.NeighborhoodRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.UserRepository;
import in.dukkan.web.dto.ShopDtos.ShopView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    public record CreateApplicationRequest(
            @NotBlank String businessName,
            @NotBlank String ownerName,
            @NotBlank String email,
            @NotBlank String phone,
            @NotBlank String address,
            String gstin,
            List<String> categoryIds,
            String notes,
            Boolean partnerDeliveryEnabled,
            Boolean shopDeliveryEnabled,
            Double lat,
            Double lng) {}

    public record ApplicationPatch(ApplicationStatus status) {}

    public record ApplicationCreated(SellerApplication application, ShopView shop) {}

    private final ApplicationRepository applications;
    private final ShopRepository shops;
    private final UserRepository users;
    private final NeighborhoodRepository neighborhoods;
    private final ShopViews shopViews;
    private final Access access;

    public ApplicationController(
            ApplicationRepository applications,
            ShopRepository shops,
            UserRepository users,
            NeighborhoodRepository neighborhoods,
            ShopViews shopViews,
            Access access) {
        this.applications = applications;
        this.shops = shops;
        this.users = users;
        this.neighborhoods = neighborhoods;
        this.shopViews = shopViews;
        this.access = access;
    }

    @GetMapping
    public List<SellerApplication> list(Authentication auth) {
        AppUser user = access.requireUser(auth);
        if (user.getRole() == Role.ADMIN) {
            return applications.findAllByOrderBySubmittedAtDesc();
        }
        return applications.findByUserIdOrderBySubmittedAtDesc(user.getId());
    }

    @PostMapping
    @Transactional
    public ApplicationCreated create(Authentication auth, @Valid @RequestBody CreateApplicationRequest request) {
        AppUser user = access.requireUser(auth);
        var neighborhood = neighborhoods.findAll().stream().findFirst();
        double lat = request.lat() != null
                ? request.lat()
                : neighborhood.map(item -> item.getLat()).orElse(28.6328);
        double lng = request.lng() != null
                ? request.lng()
                : neighborhood.map(item -> item.getLng()).orElse(77.2197);

        Shop shop = new Shop();
        shop.setId(Ids.next("shop"));
        shop.setName(request.businessName().trim());
        shop.setOwnerUserId(user.getId());
        shop.setDescription(request.notes() == null || request.notes().isBlank()
                ? request.businessName().trim() + " on GreenOwl"
                : request.notes().trim());
        shop.setAddress(request.address().trim());
        shop.setLat(lat);
        shop.setLng(lng);
        shop.setRating(BigDecimal.ZERO);
        shop.setReviewCount(0);
        shop.setVerified(request.gstin() != null && !request.gstin().isBlank());
        shop.setGstin(blankToNull(request.gstin()));
        shop.setYearStarted(java.time.Year.now().getValue());
        shop.setStatus(ShopStatus.PENDING);
        shop.setPartnerDeliveryEnabled(request.partnerDeliveryEnabled() == null || request.partnerDeliveryEnabled());
        shop.setShopDeliveryEnabled(request.shopDeliveryEnabled() == null || request.shopDeliveryEnabled());
        shop.setPartnerDeliveryFee(new BigDecimal("25"));
        shop.setShopDeliveryFee(new BigDecimal("15"));
        shop.setMinOrderAmount(new BigDecimal("99"));
        shop.setOpen(true);
        shop.setOpenTime("09:00");
        shop.setCloseTime("21:00");
        shop.setProviderType(in.dukkan.domain.ProviderType.PRODUCT_BUSINESS);
        shop.setProductsAllowed(true);
        shop.setServicesAllowed(false);
        shop.setBookingsAllowed(false);
        shop.setServiceRequestsAllowed(false);
        shop.setOrdersAllowed(true);
        shop.setQuickDeliveryAllowed(false);
        shop.setVerificationStatus(in.dukkan.domain.VerificationStatus.UNVERIFIED);
        if (request.categoryIds() != null) {
            shop.setCategoryIds(new HashSet<>(request.categoryIds()));
        }
        shops.save(shop);

        SellerApplication application = new SellerApplication();
        application.setId(Ids.next("app"));
        application.setUserId(user.getId());
        application.setShopId(shop.getId());
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setBusinessName(request.businessName().trim());
        application.setOwnerName(request.ownerName().trim());
        application.setEmail(request.email().trim());
        application.setPhone(request.phone().trim());
        application.setAddress(request.address().trim());
        application.setGstin(blankToNull(request.gstin()));
        application.setNotes(request.notes());
        application.setSubmittedAt(Instant.now());
        if (request.categoryIds() != null) {
            application.setCategoryIds(new HashSet<>(request.categoryIds()));
        }
        applications.save(application);

        user.setName(request.ownerName().trim());
        user.setEmail(request.email().trim());
        user.setPhone(request.phone().trim());
        user.setRole(Role.SELLER);
        user.setShopId(shop.getId());
        users.save(user);
        return new ApplicationCreated(application, shopViews.toView(shop));
    }

    @PatchMapping("/{id}")
    @Transactional
    public SellerApplication patch(
            Authentication auth, @PathVariable String id, @RequestBody ApplicationPatch request) {
        AppUser user = access.requireUser(auth);
        if (user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        SellerApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (request.status() == null) {
            return application;
        }
        application.setStatus(request.status());
        shops.findById(application.getShopId()).ifPresent(shop -> {
            if (request.status() == ApplicationStatus.APPROVED) {
                shop.setStatus(ShopStatus.ACTIVE);
            } else if (request.status() == ApplicationStatus.REJECTED) {
                shop.setStatus(ShopStatus.PENDING);
            }
            shops.save(shop);
        });
        users.findById(application.getUserId()).ifPresent(owner -> {
            if (request.status() == ApplicationStatus.APPROVED) {
                owner.setRole(Role.SELLER);
                owner.setShopId(application.getShopId());
                users.save(owner);
            }
        });
        return applications.save(application);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
