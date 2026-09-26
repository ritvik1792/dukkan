package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ApplicationStatus;
import in.dukkan.domain.ProviderType;
import in.dukkan.domain.Role;
import in.dukkan.domain.SellerApplication;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ApplicationRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.UserRepository;
import in.dukkan.service.SellerOnboardingService;
import in.dukkan.service.SellerOnboardingService.SellerIntent;
import in.dukkan.web.dto.ShopDtos.ShopView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
            List<String> serviceCategoryIds,
            Boolean provideServices,
            ProviderType providerType,
            String profession,
            String serviceArea,
            String notes,
            Boolean partnerDeliveryEnabled,
            Boolean shopDeliveryEnabled,
            Double lat,
            Double lng) {}

    public record ApplicationPatch(
            ApplicationStatus status,
            String reviewNote,
            String businessName,
            String ownerName,
            String email,
            String phone,
            String address,
            String gstin,
            List<String> categoryIds,
            String notes,
            String profession,
            String serviceArea,
            Boolean partnerDeliveryEnabled,
            Boolean shopDeliveryEnabled,
            Double lat,
            Double lng) {}

    public record ApplicationCreated(SellerApplication application, ShopView shop) {}

    private final ApplicationRepository applications;
    private final ShopRepository shops;
    private final UserRepository users;
    private final ShopViews shopViews;
    private final Access access;
    private final SellerOnboardingService onboarding;

    public ApplicationController(
            ApplicationRepository applications,
            ShopRepository shops,
            UserRepository users,
            ShopViews shopViews,
            Access access,
            SellerOnboardingService onboarding) {
        this.applications = applications;
        this.shops = shops;
        this.users = users;
        this.shopViews = shopViews;
        this.access = access;
        this.onboarding = onboarding;
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
        boolean provideServices = Boolean.TRUE.equals(request.provideServices())
                || (request.serviceCategoryIds() != null && !request.serviceCategoryIds().isEmpty());
        SellerIntent intent = new SellerIntent(
                request.businessName(),
                request.ownerName(),
                request.email(),
                request.phone(),
                request.address(),
                request.gstin(),
                request.notes(),
                request.categoryIds(),
                request.serviceCategoryIds(),
                provideServices,
                request.providerType(),
                request.profession(),
                request.serviceArea(),
                request.partnerDeliveryEnabled(),
                request.shopDeliveryEnabled(),
                request.lat(),
                request.lng());
        if (!SellerOnboardingService.wantsSellerAccount(intent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select shop categories or services");
        }
        var result = onboarding.upsertProfile(user, intent);
        return new ApplicationCreated(result.application(), shopViews.toView(result.shop()));
    }

    @PatchMapping("/{id}")
    @Transactional
    public SellerApplication patch(
            Authentication auth, @PathVariable String id, @RequestBody ApplicationPatch request) {
        AppUser user = access.requireUser(auth);
        SellerApplication application = applications.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean admin = user.getRole() == Role.ADMIN;
        boolean owner = user.getId().equals(application.getUserId());
        if (!admin && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        if (request == null) {
            return application;
        }

        if (hasEdits(request)) {
            if (application.getStatus() == ApplicationStatus.APPROVED && !admin) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "This dukkan is already live");
            }
            applyEdits(application, request);
            shops.findById(application.getShopId()).ifPresent(shop -> syncShop(shop, request));
            if (!admin && application.getStatus() != ApplicationStatus.SUBMITTED) {
                application.setStatus(ApplicationStatus.SUBMITTED);
                application.setSubmittedAt(Instant.now());
            }
        }

        if (request.status() != null || request.reviewNote() != null) {
            if (!admin) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            if (request.reviewNote() != null) {
                String note = request.reviewNote().trim();
                application.setReviewNote(note.isEmpty() ? null : note);
            }
            if (request.status() != null) {
                application.setStatus(request.status());
                shops.findById(application.getShopId()).ifPresent(shop -> {
                    if (request.status() == ApplicationStatus.APPROVED) {
                        shop.setStatus(ShopStatus.ACTIVE);
                    } else if (request.status() == ApplicationStatus.REJECTED) {
                        shop.setStatus(ShopStatus.PENDING);
                    }
                    shops.save(shop);
                });
                users.findById(application.getUserId()).ifPresent(ownerUser -> {
                    if (request.status() == ApplicationStatus.APPROVED) {
                        if (ownerUser.getRole() != Role.ADMIN) {
                            ownerUser.setRole(Role.SELLER);
                        }
                        ownerUser.setShopId(application.getShopId());
                        users.save(ownerUser);
                    }
                });
            }
        }
        return applications.save(application);
    }

    private static boolean hasEdits(ApplicationPatch request) {
        return request.businessName() != null
                || request.ownerName() != null
                || request.email() != null
                || request.phone() != null
                || request.address() != null
                || request.gstin() != null
                || request.categoryIds() != null
                || request.notes() != null
                || request.profession() != null
                || request.serviceArea() != null
                || request.partnerDeliveryEnabled() != null
                || request.shopDeliveryEnabled() != null
                || request.lat() != null
                || request.lng() != null;
    }

    private void applyEdits(SellerApplication application, ApplicationPatch request) {
        if (request.businessName() != null && !request.businessName().isBlank()) {
            application.setBusinessName(request.businessName().trim());
        }
        if (request.ownerName() != null && !request.ownerName().isBlank()) {
            application.setOwnerName(request.ownerName().trim());
        }
        if (request.email() != null && !request.email().isBlank()) {
            application.setEmail(request.email().trim());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            application.setPhone(request.phone().trim());
        }
        if (request.address() != null && !request.address().isBlank()) {
            application.setAddress(request.address().trim());
        }
        if (request.gstin() != null) {
            String gstin = request.gstin().trim();
            application.setGstin(gstin.isEmpty() ? null : gstin);
        }
        if (request.notes() != null) {
            application.setNotes(request.notes().trim());
        }
        if (request.categoryIds() != null) {
            Set<String> ids = new HashSet<>();
            for (String categoryId : request.categoryIds()) {
                if (categoryId != null && !categoryId.isBlank()) {
                    ids.add(categoryId.trim());
                }
            }
            application.getCategoryIds().clear();
            application.getCategoryIds().addAll(ids);
        }
        users.findById(application.getUserId()).ifPresent(owner -> {
            if (request.ownerName() != null && !request.ownerName().isBlank()) {
                owner.setName(request.ownerName().trim());
            }
            if (request.email() != null && !request.email().isBlank()) {
                String email = request.email().trim().toLowerCase(Locale.ROOT);
                if (users.findByEmailIgnoreCase(email)
                        .filter(other -> !other.getId().equals(owner.getId()))
                        .isPresent()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "That email belongs to another account and was left unchanged.");
                }
                owner.setEmail(email);
            }
            if (request.phone() != null && !request.phone().isBlank()) {
                String phone = request.phone().trim();
                if (users.findFirstByPhone(phone)
                        .filter(other -> !other.getId().equals(owner.getId()))
                        .isPresent()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "That mobile number belongs to another account and was left unchanged.");
                }
                owner.setPhone(phone);
            }
            users.save(owner);
        });
    }

    private void syncShop(Shop shop, ApplicationPatch request) {
        if (request.businessName() != null && !request.businessName().isBlank()) {
            shop.setName(request.businessName().trim());
        }
        if (request.address() != null && !request.address().isBlank()) {
            shop.setAddress(request.address().trim());
        }
        if (request.notes() != null) {
            shop.setDescription(request.notes().trim());
        }
        if (request.gstin() != null) {
            String gstin = request.gstin().trim();
            shop.setGstin(gstin.isEmpty() ? null : gstin);
            shop.setVerified(!gstin.isEmpty());
        }
        if (request.lat() != null) {
            shop.setLat(request.lat());
        }
        if (request.lng() != null) {
            shop.setLng(request.lng());
        }
        if (request.partnerDeliveryEnabled() != null) {
            shop.setPartnerDeliveryEnabled(request.partnerDeliveryEnabled());
        }
        if (request.shopDeliveryEnabled() != null) {
            shop.setShopDeliveryEnabled(request.shopDeliveryEnabled());
        }
        if (request.profession() != null) {
            String profession = request.profession().trim();
            shop.setProfession(profession.isEmpty() ? null : profession);
        }
        if (request.serviceArea() != null) {
            String area = request.serviceArea().trim();
            shop.setServiceArea(area.isEmpty() ? null : area);
        }
        if (request.categoryIds() != null) {
            Set<String> ids = new HashSet<>();
            for (String categoryId : request.categoryIds()) {
                if (categoryId != null && !categoryId.isBlank()) {
                    ids.add(categoryId.trim());
                }
            }
            shop.getCategoryIds().clear();
            shop.getCategoryIds().addAll(ids);
        }
        shops.save(shop);
    }
}
