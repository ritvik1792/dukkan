package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Review;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.repository.ReviewRepository;
import in.dukkan.repository.ShopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    public record CreateReviewRequest(
            @NotBlank String catalogProductId,
            String listingId,
            @NotBlank String shopId,
            String orderId,
            @Min(1) @Max(5) int rating,
            String title,
            @NotBlank String body,
            List<String> imageUrls) {}

    public record ReviewReplyRequest(@NotBlank String body) {}

    public record ReviewPatch(Boolean hidden, List<String> imageUrls) {}

    private final ReviewRepository reviews;
    private final ShopRepository shops;
    private final Access access;

    public ReviewController(ReviewRepository reviews, ShopRepository shops, Access access) {
        this.reviews = reviews;
        this.shops = shops;
        this.access = access;
    }

    @GetMapping
    public List<Review> list(
            @RequestParam(required = false) String catalogProductId,
            @RequestParam(required = false) String shopId) {
        if (catalogProductId != null && !catalogProductId.isBlank()) {
            return reviews.findByCatalogProductIdOrderByCreatedAtDesc(catalogProductId);
        }
        if (shopId != null && !shopId.isBlank()) {
            return reviews.findByShopIdOrderByCreatedAtDesc(shopId);
        }
        return reviews.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    @Transactional
    public Review create(Authentication auth, @Valid @RequestBody CreateReviewRequest request) {
        AppUser user = access.requireUser(auth);
        Review review = new Review();
        review.setId(Ids.next("r"));
        review.setCatalogProductId(request.catalogProductId());
        review.setListingId(blankToNull(request.listingId()));
        review.setShopId(request.shopId());
        review.setBuyerId(user.getId());
        review.setOrderId(blankToNull(request.orderId()));
        review.setRating(request.rating());
        review.setTitle(request.title() == null || request.title().isBlank() ? "Review" : request.title().trim());
        review.setBody(request.body().trim());
        review.setCreatedAt(Instant.now());
        review.setHidden(false);
        if (request.imageUrls() != null) {
            review.getImageUrls().addAll(request.imageUrls());
        }
        return reviews.save(review);
    }

    @PostMapping("/{id}/reply")
    @Transactional
    public Review reply(Authentication auth, @PathVariable String id, @Valid @RequestBody ReviewReplyRequest request) {
        AppUser user = access.requireUser(auth);
        Review review = reviews.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (user.getRole() != Role.ADMIN && !ownsShop(user, review.getShopId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        review.setSellerReplyText(request.body().trim());
        review.setSellerRepliedAt(Instant.now());
        return reviews.save(review);
    }

    @PatchMapping("/{id}")
    @Transactional
    public Review patch(Authentication auth, @PathVariable String id, @RequestBody ReviewPatch request) {
        AppUser user = access.requireUser(auth);
        Review review = reviews.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        boolean owner = user.getId().equals(review.getBuyerId());
        if (request.hidden() != null) {
            if (user.getRole() != Role.ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            review.setHidden(request.hidden());
        }
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            if (user.getRole() != Role.ADMIN && !owner) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }
            review.getImageUrls().addAll(request.imageUrls());
        }
        return reviews.save(review);
    }

    private boolean ownsShop(AppUser user, String shopId) {
        Shop shop = shops.findById(shopId).orElse(null);
        return shop != null && shop.getOwnerUserId().equals(user.getId());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
