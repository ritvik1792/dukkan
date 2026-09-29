package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Booking;
import in.dukkan.domain.BookingStatus;
import in.dukkan.domain.Category;
import in.dukkan.domain.CustomerOrder;
import in.dukkan.domain.OrderItem;
import in.dukkan.domain.OrderStatus;
import in.dukkan.domain.Review;
import in.dukkan.domain.Shop;
import in.dukkan.repository.BookingRepository;
import in.dukkan.repository.CategoryRepository;
import in.dukkan.repository.OrderRepository;
import in.dukkan.repository.ReviewRepository;
import in.dukkan.repository.ShopRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    public record Aspect(String key, String label) {}

    public record AspectGuide(String profile, List<Aspect> aspects) {}

    public record CreateCommand(
            String catalogProductId,
            String listingId,
            String serviceId,
            String shopId,
            String orderId,
            Integer productQuality,
            Integer shopExperience,
            Integer staffScore,
            String title,
            String body,
            List<String> imageUrls) {}

    private final ReviewRepository reviews;
    private final ShopRepository shops;
    private final OrderRepository orders;
    private final BookingRepository bookings;
    private final CategoryRepository categories;

    public ReviewService(
            ReviewRepository reviews,
            ShopRepository shops,
            OrderRepository orders,
            BookingRepository bookings,
            CategoryRepository categories) {
        this.reviews = reviews;
        this.shops = shops;
        this.orders = orders;
        this.bookings = bookings;
        this.categories = categories;
    }

    public AspectGuide guideForShop(String shopId) {
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        return guide(profile(shop));
    }

    @Transactional
    public Review create(AppUser buyer, CreateCommand command) {
        if (command.shopId() == null || command.shopId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop is required");
        }
        Shop shop = shops.findById(command.shopId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
        String profile = profile(shop);
        requireScore(command.productQuality(), label(profile, "productQuality"));
        requireScore(command.shopExperience(), label(profile, "shopExperience"));
        requireScore(command.staffScore(), label(profile, "staffScore"));

        String serviceId = blankToNull(command.serviceId());
        String catalogProductId = blankToNull(command.catalogProductId());
        String orderId = blankToNull(command.orderId());
        if (serviceId != null) {
            assertCompletedBooking(buyer.getId(), serviceId, shop.getId());
            if (reviews.findByBuyerIdAndServiceId(buyer.getId(), serviceId).stream()
                    .anyMatch(review -> shop.getId().equals(review.getShopId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reviewed this service");
            }
        } else {
            if (orderId == null || catalogProductId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "A delivered order is required before you can review this");
            }
            assertDeliveredItem(buyer.getId(), orderId, shop.getId(), catalogProductId, command.listingId());
            if (reviews.findByBuyerIdAndOrderId(buyer.getId(), orderId).stream()
                    .anyMatch(review -> catalogProductId.equals(review.getCatalogProductId()))) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You already reviewed this purchase");
            }
        }

        Review review = new Review();
        review.setId(Ids.next("r"));
        review.setCatalogProductId(catalogProductId);
        review.setListingId(blankToNull(command.listingId()));
        review.setServiceId(serviceId);
        review.setShopId(shop.getId());
        review.setBuyerId(buyer.getId());
        review.setOrderId(orderId);
        review.setTargetKind(serviceId != null ? "SERVICE" : "PRODUCT");
        review.setProductQuality(command.productQuality());
        review.setShopExperience(command.shopExperience());
        review.setStaffScore(command.staffScore());
        review.setRating(average(command.productQuality(), command.shopExperience(), command.staffScore()));
        review.setTitle(command.title() == null || command.title().isBlank() ? "Review" : command.title().trim());
        review.setBody(blankToNull(command.body()));
        review.setVerifiedPurchase(true);
        review.setCreatedAt(Instant.now());
        review.setHidden(false);
        if (command.imageUrls() != null) {
            review.getImageUrls().addAll(command.imageUrls());
        }
        return reviews.save(review);
    }

    private void assertDeliveredItem(
            String buyerId, String orderId, String shopId, String catalogProductId, String listingId) {
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Order not found"));
        if (!buyerId.equals(order.getBuyerId()) || !shopId.equals(order.getShopId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only review your own purchase");
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You can review this after the order is delivered");
        }
        boolean bought = false;
        for (OrderItem item : order.getItems()) {
            boolean product = catalogProductId.equals(item.getCatalogProductId());
            boolean listing = listingId != null && !listingId.isBlank() && listingId.equals(item.getListingId());
            if (product || listing) {
                bought = true;
                break;
            }
        }
        if (!bought) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "That product is not on this order");
        }
    }

    private void assertCompletedBooking(String buyerId, String serviceId, String shopId) {
        boolean completed = bookings
                .findByCustomerIdAndServiceIdAndStatus(buyerId, serviceId, BookingStatus.COMPLETED)
                .stream()
                .anyMatch(booking -> shopId.equals(booking.getProviderId()));
        if (!completed) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "You can review this service after a completed booking");
        }
    }

    private static void requireScore(Integer score, String label) {
        if (score == null || score < 1 || score > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " needs a rating from 1 to 5");
        }
    }

    private static int average(int a, int b, int c) {
        return Math.max(1, Math.min(5, Math.round((a + b + c) / 3.0f)));
    }

    public String profile(Shop shop) {
        if (shop.isServicesAllowed() && !shop.isProductsAllowed()) {
            return "SERVICE";
        }
        if (shop.getCategoryIds() != null) {
            for (String id : shop.getCategoryIds()) {
                Category category = categories.findById(id).orElse(null);
                if (category == null || category.getName() == null) {
                    continue;
                }
                String name = category.getName().toLowerCase(Locale.ROOT);
                if (name.contains("food") || name.contains("restaurant") || name.contains("bakery")) {
                    return "RESTAURANT";
                }
            }
        }
        return "RETAIL";
    }

    public AspectGuide guide(String profile) {
        return switch (profile) {
            case "SERVICE" -> new AspectGuide("SERVICE", List.of(
                    new Aspect("productQuality", "Service quality"),
                    new Aspect("shopExperience", "Business experience"),
                    new Aspect("staffScore", "Staff / professional")));
            case "RESTAURANT" -> new AspectGuide("RESTAURANT", List.of(
                    new Aspect("productQuality", "Food quality"),
                    new Aspect("shopExperience", "Restaurant experience"),
                    new Aspect("staffScore", "Staff / service")));
            default -> new AspectGuide("RETAIL", List.of(
                    new Aspect("productQuality", "Product quality"),
                    new Aspect("shopExperience", "Shop experience"),
                    new Aspect("staffScore", "Staff behavior")));
        };
    }

    private String label(String profile, String key) {
        return guide(profile).aspects().stream()
                .filter(aspect -> aspect.key().equals(key))
                .map(Aspect::label)
                .findFirst()
                .orElse(key);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
