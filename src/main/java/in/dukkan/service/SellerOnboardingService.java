package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ApplicationStatus;
import in.dukkan.domain.ProviderType;
import in.dukkan.domain.Role;
import in.dukkan.domain.SellerApplication;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.domain.SupportTicket;
import in.dukkan.domain.TicketKind;
import in.dukkan.domain.TicketMessage;
import in.dukkan.domain.TicketStatus;
import in.dukkan.domain.VerificationStatus;
import in.dukkan.repository.ApplicationRepository;
import in.dukkan.repository.NeighborhoodRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.repository.TicketRepository;
import in.dukkan.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Creates or updates a single shop/provider profile for product categories and/or services.
 */
@Service
public class SellerOnboardingService {

    public record SellerIntent(
            String businessName,
            String ownerName,
            String email,
            String phone,
            String address,
            String gstin,
            String notes,
            List<String> categoryIds,
            List<String> serviceCategoryIds,
            boolean provideServices,
            ProviderType providerType,
            String profession,
            String serviceArea,
            Boolean partnerDeliveryEnabled,
            Boolean shopDeliveryEnabled,
            Double lat,
            Double lng) {}

    public record OnboardResult(Shop shop, SellerApplication application) {}

    private final ShopRepository shops;
    private final ApplicationRepository applications;
    private final NeighborhoodRepository neighborhoods;
    private final UserRepository users;
    private final TicketRepository tickets;

    public SellerOnboardingService(
            ShopRepository shops,
            ApplicationRepository applications,
            NeighborhoodRepository neighborhoods,
            UserRepository users,
            TicketRepository tickets) {
        this.shops = shops;
        this.applications = applications;
        this.neighborhoods = neighborhoods;
        this.users = users;
        this.tickets = tickets;
    }

    public static boolean wantsSellerAccount(SellerIntent intent) {
        if (intent == null) {
            return false;
        }
        return intent.provideServices()
                || !normalizeIds(intent.categoryIds()).isEmpty()
                || !normalizeIds(intent.serviceCategoryIds()).isEmpty();
    }

    @Transactional
    public OnboardResult upsertProfile(AppUser user, SellerIntent intent) {
        if (!wantsSellerAccount(intent)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select shop categories or services");
        }
        Shop existing = findOwnedShop(user);
        if (existing != null) {
            applyIntent(existing, intent, false);
            shops.save(existing);
            promoteSeller(user, existing);
            SellerApplication application = latestApplication(user)
                    .map(current -> refreshApplication(current, intent, existing))
                    .orElseGet(() -> saveNewApplication(user, existing, intent));
            return new OnboardResult(existing, application);
        }
        String businessName = firstNonBlank(intent.businessName(), intent.ownerName(), user.getName());
        String address = intent.address() == null ? "" : intent.address().trim();
        if (businessName == null || businessName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a shop or business name");
        }
        if (address.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your shop or service address");
        }
        Shop shop = new Shop();
        shop.setId(nextShopId(intent));
        shop.setOwnerUserId(user.getId());
        shop.setName(businessName.trim());
        shop.setAddress(address);
        applyIntent(shop, intent, true);
        shops.save(shop);
        SellerApplication application = saveNewApplication(user, shop, intent);
        promoteSeller(user, shop);
        return new OnboardResult(shop, application);
    }

    private void applyIntent(Shop shop, SellerIntent intent, boolean creating) {
        Set<String> productIds = normalizeIds(intent.categoryIds());
        Set<String> serviceIds = normalizeIds(intent.serviceCategoryIds());
        boolean hasProducts = !productIds.isEmpty();

        Set<String> merged = new LinkedHashSet<>();
        if (shop.getCategoryIds() != null) {
            merged.addAll(shop.getCategoryIds());
        }
        merged.addAll(productIds);
        merged.addAll(serviceIds);
        if (shop.getCategoryIds() == null) {
            shop.setCategoryIds(new HashSet<>(merged));
        } else {
            shop.getCategoryIds().clear();
            shop.getCategoryIds().addAll(merged);
        }

        if (creating) {
            var neighborhood = neighborhoods.findAll().stream().findFirst();
            shop.setLat(intent.lat() != null
                    ? intent.lat()
                    : neighborhood.map(item -> item.getLat()).orElse(28.6328));
            shop.setLng(intent.lng() != null
                    ? intent.lng()
                    : neighborhood.map(item -> item.getLng()).orElse(77.2197));
            shop.setRating(BigDecimal.ZERO);
            shop.setReviewCount(0);
            shop.setYearStarted(Year.now().getValue());
            shop.setStatus(ShopStatus.PENDING);
            shop.setOpen(true);
            shop.setOpenTime("09:00");
            shop.setCloseTime("21:00");
            shop.setQuickDeliveryAllowed(false);
            shop.setVerificationStatus(VerificationStatus.UNVERIFIED);
            shop.setServicesAllowed(false);
            shop.setBookingsAllowed(false);
            shop.setServiceRequestsAllowed(false);
        } else if (intent.lat() != null) {
            shop.setLat(intent.lat());
        }
        if (!creating && intent.lng() != null) {
            shop.setLng(intent.lng());
        }

        if (intent.notes() != null && !intent.notes().isBlank()) {
            shop.setDescription(intent.notes().trim());
        } else if (creating) {
            shop.setDescription(shop.getName() + " on pinkCarrot");
        }
        if (intent.address() != null && !intent.address().isBlank()) {
            shop.setAddress(intent.address().trim());
        }
        if (intent.businessName() != null && !intent.businessName().isBlank()) {
            shop.setName(intent.businessName().trim());
        }
        if (intent.gstin() != null) {
            String gstin = blankToNull(intent.gstin());
            shop.setGstin(gstin);
            shop.setVerified(gstin != null);
        } else if (creating) {
            shop.setVerified(false);
        }

        ProviderType type = resolveProviderType(hasProducts, intent.providerType(), shop, creating);
        shop.setProviderType(type);
        if (creating) {
            shop.setProductsAllowed(hasProducts);
            shop.setOrdersAllowed(hasProducts);
        } else if (hasProducts) {
            shop.setProductsAllowed(true);
            shop.setOrdersAllowed(true);
        }
        boolean partners = intent.partnerDeliveryEnabled() == null
                ? (creating ? hasProducts : shop.isPartnerDeliveryEnabled())
                : intent.partnerDeliveryEnabled();
        boolean ownDelivery = intent.shopDeliveryEnabled() == null
                ? (creating ? hasProducts : shop.isShopDeliveryEnabled())
                : intent.shopDeliveryEnabled();
        shop.setPartnerDeliveryEnabled(partners);
        shop.setShopDeliveryEnabled(ownDelivery);
        if (creating) {
            shop.setPartnerDeliveryFee(hasProducts ? new BigDecimal("25") : BigDecimal.ZERO);
            shop.setShopDeliveryFee(hasProducts ? new BigDecimal("15") : BigDecimal.ZERO);
            shop.setMinOrderAmount(hasProducts ? new BigDecimal("99") : BigDecimal.ZERO);
        }
        if (intent.profession() != null) {
            shop.setProfession(blankToNull(intent.profession()));
        }
        if (intent.serviceArea() != null) {
            shop.setServiceArea(blankToNull(intent.serviceArea()));
        }
    }

    private void promoteSeller(AppUser user, Shop shop) {
        if (user.getRole() != Role.ADMIN) {
            user.setRole(Role.SELLER);
        }
        if (user.getShopId() == null || user.getShopId().isBlank()) {
            user.setShopId(shop.getId());
        }
        users.save(user);
    }

    private SellerApplication refreshApplication(SellerApplication application, SellerIntent intent, Shop shop) {
        if (application.getStatus() == ApplicationStatus.APPROVED) {
            return application;
        }
        application.setBusinessName(firstNonBlank(intent.businessName(), shop.getName()));
        application.setOwnerName(firstNonBlank(intent.ownerName(), application.getOwnerName()));
        application.setEmail(firstNonBlank(intent.email(), application.getEmail()));
        application.setPhone(firstNonBlank(intent.phone(), application.getPhone()));
        application.setAddress(firstNonBlank(intent.address(), shop.getAddress()));
        if (intent.gstin() != null) {
            application.setGstin(blankToNull(intent.gstin()));
        }
        if (intent.notes() != null) {
            application.setNotes(intent.notes());
        }
        if (shop.getCategoryIds() != null) {
            application.getCategoryIds().clear();
            application.getCategoryIds().addAll(shop.getCategoryIds());
        }
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setSubmittedAt(Instant.now());
        return applications.save(application);
    }

    private SellerApplication saveNewApplication(AppUser user, Shop shop, SellerIntent intent) {
        SellerApplication application = applications.save(newApplication(user, shop, intent));
        openApprovalTicket(user, shop, application);
        return application;
    }

    private void openApprovalTicket(AppUser user, Shop shop, SellerApplication application) {
        Instant now = Instant.now();
        SupportTicket ticket = new SupportTicket();
        ticket.setId(Ids.next("tk"));
        ticket.setKind(TicketKind.SUPPORT);
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setSubject("Application: " + application.getBusinessName());
        ticket.setBuyerId(user.getId());
        ticket.setShopId(shop.getId());
        ticket.setCreatedAt(now);
        ticket.setHidden(false);
        TicketMessage message = new TicketMessage();
        message.setId(Ids.next("m"));
        message.setTicket(ticket);
        message.setAuthorId(user.getId());
        String notes = application.getNotes();
        message.setBody(notes == null || notes.isBlank()
                ? "I submitted my seller application and I'm waiting for approval."
                : notes);
        message.setCreatedAt(now);
        ticket.getMessages().add(message);
        tickets.save(ticket);
    }

    private SellerApplication newApplication(AppUser user, Shop shop, SellerIntent intent) {
        SellerApplication application = new SellerApplication();
        application.setId(Ids.next("app"));
        application.setUserId(user.getId());
        application.setShopId(shop.getId());
        application.setStatus(ApplicationStatus.SUBMITTED);
        application.setBusinessName(firstNonBlank(intent.businessName(), shop.getName(), user.getName()));
        application.setOwnerName(firstNonBlank(intent.ownerName(), user.getName()));
        application.setEmail(firstNonBlank(intent.email(), user.getEmail()));
        application.setPhone(firstNonBlank(intent.phone(), user.getPhone(), ""));
        application.setAddress(firstNonBlank(intent.address(), shop.getAddress()));
        application.setGstin(blankToNull(intent.gstin()));
        application.setNotes(intent.notes());
        application.setSubmittedAt(Instant.now());
        application.setCategoryIds(new HashSet<>(shop.getCategoryIds()));
        return application;
    }

    private Shop findOwnedShop(AppUser user) {
        if (user.getShopId() != null && !user.getShopId().isBlank()) {
            Shop shop = shops.findById(user.getShopId()).orElse(null);
            if (shop != null) {
                return shop;
            }
        }
        return shops.findByOwnerUserId(user.getId()).stream().findFirst().orElse(null);
    }

    private java.util.Optional<SellerApplication> latestApplication(AppUser user) {
        return applications.findByUserIdOrderBySubmittedAtDesc(user.getId()).stream().findFirst();
    }

    private static ProviderType resolveProviderType(
            boolean hasProducts, ProviderType requested, Shop shop, boolean creating) {
        boolean shopHasProducts = hasProducts || (!creating && shop.isProductsAllowed());
        if (shopHasProducts) {
            return ProviderType.PRODUCT_BUSINESS;
        }
        if (requested != null) {
            return requested;
        }
        if (!creating && shop.getProviderType() != null) {
            return shop.getProviderType();
        }
        return ProviderType.SERVICE_BUSINESS;
    }

    private static String nextShopId(SellerIntent intent) {
        boolean products = !normalizeIds(intent.categoryIds()).isEmpty();
        if (!products && intent.providerType() == ProviderType.INDIVIDUAL) {
            return Ids.next("prv");
        }
        return Ids.next("shop");
    }

    static Set<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                out.add(id.trim());
            }
        }
        return out;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
