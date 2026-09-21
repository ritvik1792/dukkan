package in.dukkan.web;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.Offer;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.RequestShop;
import in.dukkan.repository.RequestShopRepository;
import in.dukkan.service.ProductRequestService;
import in.dukkan.service.ProductRequestService.CreateRequestInput;
import in.dukkan.service.ProductRequestService.MerchantRespondInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductRequestController {

    public record CreateBody(
            @NotNull String catalogProductId,
            String listingId,
            String queryText,
            @NotNull Double buyerLat,
            @NotNull Double buyerLng) {}

    public record RespondBody(
            @NotNull String decision,
            BigDecimal unitPrice,
            Integer availableQty,
            String listingId,
            String message) {}

    private final ProductRequestService productRequests;
    private final RequestShopRepository requestShops;
    private final Access access;

    public ProductRequestController(
            ProductRequestService productRequests, RequestShopRepository requestShops, Access access) {
        this.productRequests = productRequests;
        this.requestShops = requestShops;
        this.access = access;
    }

    @PostMapping("/api/product-requests")
    public Map<String, Object> create(Authentication auth, @Valid @RequestBody CreateBody body) {
        AppUser buyer = access.requireUser(auth);
        ProductRequest request = productRequests.create(
                buyer,
                new CreateRequestInput(
                        body.catalogProductId(),
                        body.listingId(),
                        body.queryText(),
                        body.buyerLat(),
                        body.buyerLng()));
        return requestDetail(request);
    }

    @GetMapping("/api/product-requests/{id}")
    public Map<String, Object> one(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireUser(auth);
        ProductRequest request = productRequests.getForBuyer(user, id);
        return requestDetail(request);
    }

    @GetMapping("/api/product-requests/{id}/offers")
    public List<Offer> offers(Authentication auth, @PathVariable String id) {
        return productRequests.listOffers(access.requireUser(auth), id);
    }

    @PostMapping("/api/product-requests/{id}/cancel")
    public Map<String, Object> cancel(Authentication auth, @PathVariable String id) {
        ProductRequest request = productRequests.cancel(access.requireUser(auth), id);
        return requestDetail(request);
    }

    @GetMapping("/api/merchant/requests")
    public List<Map<String, Object>> merchantInbox(Authentication auth) {
        return productRequests.merchantInbox(access.requireSellerOrAdmin(auth));
    }

    @GetMapping("/api/merchant/requests/{id}")
    public Map<String, Object> merchantOne(Authentication auth, @PathVariable String id) {
        return productRequests.merchantRequestDetail(access.requireSellerOrAdmin(auth), id);
    }

    @PostMapping("/api/merchant/requests/{id}/respond")
    public Map<String, Object> respond(
            Authentication auth, @PathVariable String id, @Valid @RequestBody RespondBody body) {
        Offer offer = productRequests.respond(
                access.requireSellerOrAdmin(auth),
                id,
                new MerchantRespondInput(
                        body.decision(),
                        body.unitPrice(),
                        body.availableQty(),
                        body.listingId(),
                        body.message()));
        Map<String, Object> out = new HashMap<>();
        out.put("requestId", id);
        out.put("offer", offer);
        out.put("declined", offer == null);
        return out;
    }

    @PostMapping("/api/offers/{id}/select")
    public Offer select(Authentication auth, @PathVariable String id) {
        return productRequests.selectOffer(access.requireUser(auth), id);
    }

    private Map<String, Object> requestDetail(ProductRequest request) {
        Map<String, Object> out = new HashMap<>();
        out.put("request", request);
        List<RequestShop> shops = requestShops.findByRequestIdOrderByRankScoreDesc(request.getId());
        out.put("shops", shops);
        out.put("notifiedCount", shops.stream().filter(s -> s.getNotifiedAt() != null).count());
        return out;
    }
}
