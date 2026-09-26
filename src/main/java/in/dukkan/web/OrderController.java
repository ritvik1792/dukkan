package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.CustomerOrder;
import in.dukkan.domain.DeliveryMode;
import in.dukkan.domain.Listing;
import in.dukkan.domain.Offer;
import in.dukkan.domain.OrderEvent;
import in.dukkan.domain.OrderItem;
import in.dukkan.domain.OrderStatus;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.OrderRepository;
import in.dukkan.repository.ShopPartnerRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ProductRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record CartLine(@NotBlank String listingId, @Min(1) int quantity, DeliveryMode deliveryMode) {}

    public record PlaceOrderRequest(
            @NotEmpty List<CartLine> items,
            @NotBlank String address,
            String paymentMethod,
            String paymentStatus,
            String paymentRefId,
            BigDecimal discount,
            String couponCode,
            String requestId,
            String offerId) {}

    public record OrderPatch(
            OrderStatus status, String partnerId, Instant packingBy, Instant readyBy, Instant deliverBy) {}

    private final OrderRepository orders;
    private final ListingRepository listings;
    private final ShopRepository shops;
    private final ShopPartnerRepository shopPartners;
    private final ProductRequestService productRequests;
    private final Access access;

    public OrderController(
            OrderRepository orders,
            ListingRepository listings,
            ShopRepository shops,
            ShopPartnerRepository shopPartners,
            ProductRequestService productRequests,
            Access access) {
        this.orders = orders;
        this.listings = listings;
        this.shops = shops;
        this.shopPartners = shopPartners;
        this.productRequests = productRequests;
        this.access = access;
    }

    @GetMapping
    public List<CustomerOrder> list(Authentication auth) {
        AppUser user = access.requireUser(auth);
        if (user.getRole() == Role.ADMIN) {
            return orders.findAllByOrderByCreatedAtDesc();
        }
        Map<String, CustomerOrder> merged = new LinkedHashMap<>();
        for (CustomerOrder order : orders.findByBuyerIdOrderByCreatedAtDesc(user.getId())) {
            merged.put(order.getId(), order);
        }
        if (user.getRole() == Role.SELLER) {
            for (Shop shop : shops.findByOwnerUserId(user.getId())) {
                for (CustomerOrder order : orders.findByShopIdOrderByCreatedAtDesc(shop.getId())) {
                    merged.put(order.getId(), order);
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @GetMapping("/{id}")
    public CustomerOrder one(Authentication auth, @PathVariable String id) {
        CustomerOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanView(access.requireUser(auth), order);
        return order;
    }

    @PostMapping
    @Transactional
    public CustomerOrder place(Authentication auth, @Valid @RequestBody PlaceOrderRequest request) {
        String buyerId = access.userId(auth);
        Offer linkedOffer = null;
        if (request.requestId() != null
                || request.offerId() != null) {
            if (request.requestId() == null || request.offerId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "requestId and offerId must be provided together");
            }
            linkedOffer = productRequests.ensureSelectedForOrder(buyerId, request.requestId(), request.offerId());
        }

        CartLine first = request.items().get(0);
        Listing firstListing = listings.findById(first.listingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing not found"));
        Shop shop = shops.findById(firstListing.getShopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found"));
        if (linkedOffer != null && !shop.getId().equals(linkedOffer.getShopId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order shop must match selected offer");
        }

        Instant now = Instant.now();
        CustomerOrder order = new CustomerOrder();
        order.setId(Ids.next("ORD"));
        order.setBuyerId(buyerId);
        order.setShopId(shop.getId());
        order.setDeliveryMode(first.deliveryMode());
        order.setStatus(OrderStatus.PLACED);
        order.setAddress(request.address());
        order.setCreatedAt(now);
        if (request.paymentMethod() != null && !request.paymentMethod().isBlank()) {
            order.setPaymentMethod(request.paymentMethod());
            order.setPaymentStatus(request.paymentStatus() != null
                    ? request.paymentStatus()
                    : ("cod".equalsIgnoreCase(request.paymentMethod()) ? "cod" : "paid"));
            order.setPaymentRefId(request.paymentRefId());
        }
        order.setDiscount(request.discount() == null ? BigDecimal.ZERO : request.discount());
        order.setCouponCode(request.couponCode());
        if (linkedOffer != null) {
            order.setRequestId(request.requestId());
            order.setOfferId(request.offerId());
        }
        order.getTimeline().add(new OrderEvent(OrderStatus.PLACED, now));

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal deliveryFee = BigDecimal.ZERO;
        for (CartLine line : request.items()) {
            Listing listing = listings.findById(line.listingId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing not found"));
            if (!listing.getShopId().equals(shop.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Items must be from one shop");
            }
            BigDecimal unitPrice = listing.getSellerPrice();
            if (linkedOffer != null
                    && listing.getId().equals(linkedOffer.getListingId())) {
                unitPrice = linkedOffer.getUnitPrice();
            }
            BigDecimal fee = line.deliveryMode() == DeliveryMode.PARTNER
                    ? shop.getPartnerDeliveryFee()
                    : shop.getShopDeliveryFee();
            OrderItem item = new OrderItem();
            item.setId(Ids.next("oi"));
            item.setOrder(order);
            item.setListingId(listing.getId());
            item.setCatalogProductId(listing.getCatalogProductId());
            item.setQuantity(line.quantity());
            item.setUnitPrice(unitPrice);
            item.setDeliveryMode(line.deliveryMode());
            item.setDeliveryFee(fee);
            item.setWarranty(listing.getWarranty());
            order.getItems().add(item);
            subtotal = subtotal.add(unitPrice.multiply(BigDecimal.valueOf(line.quantity())));
            if (deliveryFee.compareTo(BigDecimal.ZERO) == 0) {
                deliveryFee = fee;
            }
        }
        BigDecimal discount = order.getDiscount() == null ? BigDecimal.ZERO : order.getDiscount();
        order.setSubtotal(subtotal.subtract(discount).max(BigDecimal.ZERO));
        order.setDeliveryFee(deliveryFee);
        order.setTotal(order.getSubtotal().add(deliveryFee));
        return orders.save(order);
    }

    @PatchMapping("/{id}")
    @Transactional
    public CustomerOrder patch(Authentication auth, @PathVariable String id, @RequestBody OrderPatch request) {
        CustomerOrder order = orders.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        AppUser user = access.requireUser(auth);
        assertCanManage(user, order);
        if (request.status() != null && request.status() != order.getStatus()) {
            order.setStatus(request.status());
            order.getTimeline().add(new OrderEvent(request.status(), Instant.now()));
        }
        if (request.partnerId() != null) {
            String partnerId = request.partnerId().isBlank() ? null : request.partnerId();
            if (partnerId != null
                    && !shopPartners.existsByShopIdAndPartnerId(order.getShopId(), partnerId)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Rider is not assigned to this dukkan");
            }
            order.setPartnerId(partnerId);
        }
        if (request.packingBy() != null) {
            order.setPackingBy(request.packingBy());
        }
        if (request.readyBy() != null) {
            order.setReadyBy(request.readyBy());
        }
        if (request.deliverBy() != null) {
            order.setDeliverBy(request.deliverBy());
        }
        return orders.save(order);
    }

    private void assertCanView(AppUser user, CustomerOrder order) {
        if (user.getRole() == Role.ADMIN || user.getId().equals(order.getBuyerId())) {
            return;
        }
        if (ownsShop(user, order.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private void assertCanManage(AppUser user, CustomerOrder order) {
        if (user.getRole() == Role.ADMIN || ownsShop(user, order.getShopId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private boolean ownsShop(AppUser user, String shopId) {
        return shops.findById(shopId)
                .map(shop -> shop.getOwnerUserId().equals(user.getId())
                        || shopId.equals(user.getShopId()))
                .orElse(false);
    }
}
