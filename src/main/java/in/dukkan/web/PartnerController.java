package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.CustomerOrder;
import in.dukkan.domain.OrderStatus;
import in.dukkan.domain.Partner;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopPartner;
import in.dukkan.repository.OrderRepository;
import in.dukkan.repository.PartnerRepository;
import in.dukkan.repository.ShopPartnerRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.web.dto.ShopDtos.ShopView;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class PartnerController {

    public record ShopRiderView(
            String id, String name, String phone, String vehicle, boolean available) {}

    public record PartnerWrite(
            String shopId, String name, String phone, String vehicle, Boolean available) {}

    public record DeliveryOrderView(
            String id,
            String status,
            String deliveryMode,
            String partnerId,
            String address,
            Instant createdAt,
            Instant deliverBy) {}

    public record DeliverySnapshot(
            ShopView shop,
            List<ShopRiderView> partners,
            List<ShopRiderView> pool,
            List<DeliveryOrderView> activeOrders) {}

    private static final List<OrderStatus> ACTIVE_DELIVERY = List.of(
            OrderStatus.PACKING, OrderStatus.ASSIGNED, OrderStatus.READY_FOR_DELIVERY, OrderStatus.OUT_FOR_DELIVERY);

    private final PartnerRepository partners;
    private final ShopPartnerRepository shopPartners;
    private final ShopRepository shops;
    private final OrderRepository orders;
    private final ShopViews shopViews;
    private final Access access;

    public PartnerController(
            PartnerRepository partners,
            ShopPartnerRepository shopPartners,
            ShopRepository shops,
            OrderRepository orders,
            ShopViews shopViews,
            Access access) {
        this.partners = partners;
        this.shopPartners = shopPartners;
        this.shops = shops;
        this.orders = orders;
        this.shopViews = shopViews;
        this.access = access;
    }

    @GetMapping("/api/seller/partners")
    public List<ShopRiderView> sellerPartners(
            Authentication auth, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        if (user.getRole() == Role.ADMIN && (shopId == null || shopId.isBlank())) {
            return partners.findAssignedToAnyShop().stream().map(this::toView).toList();
        }
        return ridersForShop(resolveSellerShop(user, shopId).getId());
    }

    @GetMapping("/api/seller/partners/pool")
    public List<ShopRiderView> sellerPartnerPool(
            Authentication auth, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveSellerShop(user, shopId);
        return partners.findUnassignedToShop(shop.getId()).stream().map(this::toView).toList();
    }

    @GetMapping("/api/seller/delivery")
    public DeliverySnapshot sellerDelivery(
            Authentication auth, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveSellerShop(user, shopId);
        return snapshotFor(shop);
    }

    @PostMapping("/api/seller/partners")
    @Transactional
    public ShopRiderView createSellerPartner(
            Authentication auth, @RequestBody PartnerWrite request, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveSellerShop(user, firstNonBlank(request.shopId(), shopId));
        return createAndAssign(shop, request);
    }

    @PostMapping("/api/seller/partners/{id}/assign")
    @Transactional
    public ShopRiderView assignSellerPartner(
            Authentication auth, @PathVariable String id, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveSellerShop(user, shopId);
        return assign(shop, id);
    }

    @PatchMapping("/api/seller/partners/{id}")
    @Transactional
    public ShopRiderView patchSellerPartner(
            Authentication auth,
            @PathVariable String id,
            @RequestBody PartnerWrite request,
            @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        resolveSellerShop(user, firstNonBlank(request.shopId(), shopId));
        return patchPartner(id, request);
    }

    @DeleteMapping("/api/seller/partners/{id}")
    @Transactional
    public void unassignSellerPartner(
            Authentication auth, @PathVariable String id, @RequestParam(required = false) String shopId) {
        AppUser user = access.requireSellerOrAdmin(auth);
        Shop shop = resolveSellerShop(user, shopId);
        unassign(shop, id);
    }

    @GetMapping("/api/admin/partners")
    public List<ShopRiderView> adminPartners(@RequestParam(required = false) String shopId) {
        if (shopId == null || shopId.isBlank()) {
            return partners.findAssignedToAnyShop().stream().map(this::toView).toList();
        }
        requireShop(shopId);
        return ridersForShop(shopId);
    }

    @GetMapping("/api/admin/partners/pool")
    public List<ShopRiderView> adminPartnerPool(@RequestParam String shopId) {
        requireShop(shopId);
        return partners.findUnassignedToShop(shopId).stream().map(this::toView).toList();
    }

    @GetMapping("/api/admin/delivery")
    public DeliverySnapshot adminDelivery(@RequestParam String shopId) {
        return snapshotFor(requireShop(shopId));
    }

    @GetMapping("/api/admin/shops/{shopId}/delivery")
    public DeliverySnapshot adminShopDelivery(@PathVariable String shopId) {
        return snapshotFor(requireShop(shopId));
    }

    @GetMapping("/api/admin/shops/{shopId}/partners")
    public List<ShopRiderView> adminShopPartners(@PathVariable String shopId) {
        requireShop(shopId);
        return ridersForShop(shopId);
    }

    @PostMapping("/api/admin/partners")
    @Transactional
    public ShopRiderView createAdminPartner(
            @RequestBody PartnerWrite request, @RequestParam(required = false) String shopId) {
        Shop shop = requireShop(firstNonBlank(request.shopId(), shopId));
        return createAndAssign(shop, request);
    }

    @PostMapping("/api/admin/partners/{id}/assign")
    @Transactional
    public ShopRiderView assignAdminPartner(
            @PathVariable String id, @RequestParam(required = false) String shopId, @RequestBody(required = false) PartnerWrite body) {
        String resolved = shopId;
        if (resolved == null && body != null) {
            resolved = body.shopId();
        }
        Shop shop = requireShop(resolved);
        return assign(shop, id);
    }

    @PatchMapping("/api/admin/partners/{id}")
    @Transactional
    public ShopRiderView patchAdminPartner(@PathVariable String id, @RequestBody PartnerWrite request) {
        return patchPartner(id, request);
    }

    @DeleteMapping("/api/admin/partners/{id}")
    @Transactional
    public void unassignAdminPartner(@PathVariable String id, @RequestParam String shopId) {
        unassign(requireShop(shopId), id);
    }

    private DeliverySnapshot snapshotFor(Shop shop) {
        return new DeliverySnapshot(
                shopViews.toView(shop),
                ridersForShop(shop.getId()),
                partners.findUnassignedToShop(shop.getId()).stream().map(this::toView).toList(),
                orders.findByShopIdAndStatusInOrderByCreatedAtDesc(shop.getId(), ACTIVE_DELIVERY).stream()
                        .map(this::toOrderView)
                        .toList());
    }

    private ShopRiderView createAndAssign(Shop shop, PartnerWrite request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }
        if (request.phone() == null || request.phone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phone is required");
        }
        Partner rider = new Partner();
        rider.setId(Ids.next("ptn"));
        rider.setName(request.name().trim());
        rider.setPhone(request.phone().trim());
        rider.setVehicle(request.vehicle() == null || request.vehicle().isBlank() ? "Bike" : request.vehicle().trim());
        rider.setAvailable(request.available() == null || request.available());
        partners.save(rider);
        return assign(shop, rider.getId());
    }

    private ShopRiderView assign(Shop shop, String partnerId) {
        Partner rider = partners.findById(partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found"));
        if (!shopPartners.existsByShopIdAndPartnerId(shop.getId(), partnerId)) {
            shopPartners.save(new ShopPartner(shop.getId(), partnerId));
        }
        return toView(rider);
    }

    private ShopRiderView patchPartner(String id, PartnerWrite request) {
        Partner rider = partners.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found"));
        if (request.name() != null && !request.name().isBlank()) {
            rider.setName(request.name().trim());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            rider.setPhone(request.phone().trim());
        }
        if (request.vehicle() != null && !request.vehicle().isBlank()) {
            rider.setVehicle(request.vehicle().trim());
        }
        if (request.available() != null) {
            rider.setAvailable(request.available());
        }
        return toView(partners.save(rider));
    }

    private void unassign(Shop shop, String partnerId) {
        if (!partners.existsById(partnerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rider not found");
        }
        shopPartners.deleteByShopIdAndPartnerId(shop.getId(), partnerId);
    }

    private List<ShopRiderView> ridersForShop(String shopId) {
        return partners.findByShopId(shopId).stream().map(this::toView).toList();
    }

    private ShopRiderView toView(Partner partner) {
        return new ShopRiderView(
                partner.getId(),
                partner.getName(),
                partner.getPhone(),
                partner.getVehicle(),
                partner.isAvailable());
    }

    private DeliveryOrderView toOrderView(CustomerOrder order) {
        return new DeliveryOrderView(
                order.getId(),
                order.getStatus().toJson(),
                order.getDeliveryMode().toJson(),
                order.getPartnerId(),
                order.getAddress(),
                order.getCreatedAt(),
                order.getDeliverBy());
    }

    private Shop resolveSellerShop(AppUser user, String requestedShopId) {
        if (requestedShopId != null && !requestedShopId.isBlank()) {
            Shop shop = requireShop(requestedShopId);
            assertCanViewShop(user, shop);
            return shop;
        }
        if (user.getShopId() != null && !user.getShopId().isBlank()) {
            Shop shop = requireShop(user.getShopId());
            assertCanViewShop(user, shop);
            return shop;
        }
        if (user.getRole() == Role.ADMIN) {
            return shops.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No shops yet"));
        }
        return shops.findByOwnerUserId(user.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No shop for this account"));
    }

    private Shop requireShop(String shopId) {
        if (shopId == null || shopId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopId is required");
        }
        return shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
    }

    private void assertCanViewShop(AppUser user, Shop shop) {
        if (user.getRole() == Role.ADMIN || shop.getOwnerUserId().equals(user.getId())
                || shop.getId().equals(user.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
