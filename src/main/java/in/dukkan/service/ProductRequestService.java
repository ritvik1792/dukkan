package in.dukkan.service;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.Listing;
import in.dukkan.domain.Offer;
import in.dukkan.domain.OfferStatus;
import in.dukkan.domain.PlatformSettings;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.ProductRequestStatus;
import in.dukkan.domain.RequestEvent;
import in.dukkan.domain.RequestShop;
import in.dukkan.domain.RequestShopStatus;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.OfferRepository;
import in.dukkan.repository.ProductRequestRepository;
import in.dukkan.repository.RequestEventRepository;
import in.dukkan.repository.RequestShopRepository;
import in.dukkan.repository.SettingsRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.RequestParser.ParsedRequest;
import in.dukkan.service.ShopMatcher.RankedCandidate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductRequestService {

    public record CreateRequestInput(
            String catalogProductId, String listingId, String queryText, Double buyerLat, Double buyerLng) {}

    public record MerchantRespondInput(
            String decision, BigDecimal unitPrice, Integer availableQty, String listingId, String message) {}

    private final ProductRequestRepository requests;
    private final RequestShopRepository requestShops;
    private final OfferRepository offers;
    private final RequestEventRepository events;
    private final ListingRepository listings;
    private final ShopRepository shops;
    private final SettingsRepository settings;
    private final RequestParser requestParser;
    private final ShopMatcher matching;
    private final NotificationService notifications;

    public ProductRequestService(
            ProductRequestRepository requests,
            RequestShopRepository requestShops,
            OfferRepository offers,
            RequestEventRepository events,
            ListingRepository listings,
            ShopRepository shops,
            SettingsRepository settings,
            RequestParser requestParser,
            ShopMatcher matching,
            NotificationService notifications) {
        this.requests = requests;
        this.requestShops = requestShops;
        this.offers = offers;
        this.events = events;
        this.listings = listings;
        this.shops = shops;
        this.settings = settings;
        this.requestParser = requestParser;
        this.matching = matching;
        this.notifications = notifications;
    }

    @Transactional
    public ProductRequest create(AppUser buyer, CreateRequestInput input) {
        if (input.buyerLat() == null || input.buyerLng() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "buyerLat and buyerLng are required");
        }
        ParsedRequest parsed = requestParser.parse(input.catalogProductId(), input.listingId(), input.queryText());
        PlatformSettings config = settings.findById("default").orElseThrow();
        Instant now = Instant.now();

        ProductRequest request = new ProductRequest();
        request.setId(Ids.next("req"));
        request.setBuyerId(buyer.getId());
        request.setCatalogProductId(parsed.catalogProductId());
        request.setListingId(parsed.listingId());
        request.setQueryText(parsed.queryText());
        request.setBuyerLat(input.buyerLat());
        request.setBuyerLng(input.buyerLng());
        request.setStatus(ProductRequestStatus.OPEN);
        request.setWaveIndex(0);
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setExpiresAt(now.plusSeconds(Math.max(60, config.getOfferExpirySeconds())));
        requests.save(request);
        writeEvent(request.getId(), null, null, buyer.getId(), "REQUEST_CREATED", Map.of(
                "catalogProductId", parsed.catalogProductId(),
                "listingId", parsed.listingId() == null ? "" : parsed.listingId()));

        List<RankedCandidate> candidates =
                matching.findCandidates(parsed.catalogProductId(), input.buyerLat(), input.buyerLng());
        if (candidates.isEmpty()) {
            request.setStatus(ProductRequestStatus.AWAITING_OFFERS);
            request.setUpdatedAt(now);
            requests.save(request);
            writeEvent(request.getId(), null, null, buyer.getId(), "NO_CANDIDATES", Map.of());
            return request;
        }

        int waveSize = Math.max(1, config.getRequestWaveSize());
        List<RequestShop> rows = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            RankedCandidate candidate = candidates.get(i);
            int wave = i / waveSize;
            RequestShop row = new RequestShop();
            row.setId(Ids.next("rs"));
            row.setRequestId(request.getId());
            row.setShopId(candidate.shop().getId());
            row.setListingId(candidate.listing().getId());
            row.setWaveIndex(wave);
            row.setRankScore(ShopMatchingService.roundScore(candidate.rankScore()));
            row.setDistanceKm(ShopMatchingService.roundKm(candidate.distanceKm()));
            row.setStatus(wave == 0 ? RequestShopStatus.NOTIFIED : RequestShopStatus.QUEUED);
            if (wave == 0) {
                row.setNotifiedAt(now);
            }
            rows.add(row);
        }
        requestShops.saveAll(rows);

        request.setStatus(ProductRequestStatus.AWAITING_OFFERS);
        request.setWaveIndex(0);
        request.setUpdatedAt(now);
        requests.save(request);
        writeEvent(request.getId(), null, null, buyer.getId(), "WAVE_NOTIFIED", Map.of(
                "waveIndex", 0,
                "shopCount", rows.stream().filter(r -> r.getStatus() == RequestShopStatus.NOTIFIED).count()));

        for (RequestShop row : rows) {
            if (row.getStatus() == RequestShopStatus.NOTIFIED) {
                notifications.notifyMerchantOfRequest(request, row);
            }
        }
        return request;
    }

    @Transactional
    public ProductRequest getForBuyer(AppUser user, String requestId) {
        ProductRequest request = requireRequest(requestId);
        enforceExpiry(request);
        if (!request.getBuyerId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        maybeAdvanceWaves(request);
        return request;
    }

    @Transactional
    public ProductRequest cancel(AppUser buyer, String requestId) {
        ProductRequest request = requireRequest(requestId);
        if (!request.getBuyerId().equals(buyer.getId()) && buyer.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        enforceExpiry(request);
        if (request.getStatus().isTerminal()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is already closed");
        }
        transition(request, ProductRequestStatus.CANCELLED, buyer.getId(), "REQUEST_CANCELLED", Map.of());
        return request;
    }

    @Transactional(readOnly = true)
    public List<Offer> listOffers(AppUser user, String requestId) {
        ProductRequest request = requireRequest(requestId);
        if (!request.getBuyerId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        expireOffersForRequest(requestId);
        return offers.findByRequestIdOrderByCreatedAtAsc(requestId);
    }

    @Transactional
    public List<Map<String, Object>> merchantInbox(AppUser seller) {
        List<Shop> owned = ownedShops(seller);
        if (owned.isEmpty()) {
            return List.of();
        }
        Set<String> shopIds = owned.stream().map(Shop::getId).collect(java.util.stream.Collectors.toSet());
        advanceDueWavesInternal();
        List<RequestShop> rows = requestShops.findByShopIdInAndStatusInOrderByNotifiedAtDesc(
                shopIds,
                EnumSet.of(
                        RequestShopStatus.NOTIFIED,
                        RequestShopStatus.VIEWED,
                        RequestShopStatus.ACCEPTED,
                        RequestShopStatus.DECLINED));
        List<Map<String, Object>> out = new ArrayList<>();
        for (RequestShop row : rows) {
            ProductRequest request = requests.findById(row.getRequestId()).orElse(null);
            if (request == null) {
                continue;
            }
            enforceExpiry(request);
            if (request.getStatus().isTerminal() && row.getStatus() == RequestShopStatus.NOTIFIED) {
                continue;
            }
            Map<String, Object> item = new HashMap<>();
            item.put("requestShop", row);
            item.put("request", request);
            item.put("shopId", row.getShopId());
            out.add(item);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> merchantRequestDetail(AppUser seller, String requestId) {
        ProductRequest request = requireRequest(requestId);
        enforceExpiry(request);
        maybeAdvanceWaves(request);
        RequestShop row = findOwnedRequestShop(seller, requestId);
        if (row.getStatus() == RequestShopStatus.NOTIFIED) {
            row.setStatus(RequestShopStatus.VIEWED);
            requestShops.save(row);
            writeEvent(requestId, row.getId(), null, seller.getId(), "REQUEST_VIEWED", Map.of("shopId", row.getShopId()));
        }
        Map<String, Object> out = new HashMap<>();
        out.put("request", request);
        out.put("requestShop", row);
        out.put(
                "offers",
                offers.findByRequestIdOrderByCreatedAtAsc(requestId).stream()
                        .filter(o -> o.getShopId().equals(row.getShopId()))
                        .toList());
        return out;
    }

    @Transactional
    public Offer respond(AppUser seller, String requestId, MerchantRespondInput input) {
        ProductRequest request = requireRequest(requestId);
        enforceExpiry(request);
        if (!request.getStatus().acceptsMerchantResponse()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is not accepting responses");
        }
        RequestShop row = findOwnedRequestShop(seller, requestId);
        if (row.getStatus().hasResponded()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Shop already responded to this request");
        }
        if (row.getStatus() != RequestShopStatus.NOTIFIED && row.getStatus() != RequestShopStatus.VIEWED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop was not notified for this request");
        }

        String decision = input.decision() == null ? "" : input.decision().trim().toUpperCase();
        Instant now = Instant.now();
        row.setRespondedAt(now);
        if (row.getNotifiedAt() != null) {
            row.setResponseLatencyMs(Math.max(0, now.toEpochMilli() - row.getNotifiedAt().toEpochMilli()));
        }

        if ("REJECTED".equals(decision) || "DECLINED".equals(decision) || "NO".equals(decision)) {
            row.setStatus(RequestShopStatus.DECLINED);
            requestShops.save(row);
            writeEvent(requestId, row.getId(), null, seller.getId(), "MERCHANT_DECLINED", Map.of(
                    "shopId", row.getShopId(),
                    "latencyMs", row.getResponseLatencyMs() == null ? 0 : row.getResponseLatencyMs()));
            request.setUpdatedAt(now);
            requests.save(request);
            return null;
        }

        if (!"ACCEPTED".equals(decision) && !"YES".equals(decision) && !"ACCEPT".equals(decision)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be ACCEPTED or REJECTED");
        }
        if (input.unitPrice() == null || input.unitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unitPrice is required for ACCEPTED");
        }
        int qty = input.availableQty() == null ? 1 : input.availableQty();
        if (qty < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "availableQty must be at least 1");
        }

        String listingId = resolveListingId(request, row, input.listingId());
        Listing listing = listings
                .findById(listingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing not found"));
        if (!listing.getShopId().equals(row.getShopId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing does not belong to this shop");
        }
        if (!listing.getCatalogProductId().equals(request.getCatalogProductId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing product mismatch");
        }

        PlatformSettings config = settings.findById("default").orElseThrow();
        Offer offer = new Offer();
        offer.setId(Ids.next("offr"));
        offer.setRequestId(requestId);
        offer.setRequestShopId(row.getId());
        offer.setShopId(row.getShopId());
        offer.setListingId(listingId);
        offer.setUnitPrice(input.unitPrice());
        offer.setAvailableQty(qty);
        offer.setMessage(blankToNull(input.message()));
        offer.setStatus(OfferStatus.ACTIVE);
        offer.setCreatedAt(now);
        offer.setExpiresAt(now.plusSeconds(Math.max(60, config.getOfferExpirySeconds())));
        offers.save(offer);

        row.setStatus(RequestShopStatus.ACCEPTED);
        row.setListingId(listingId);
        requestShops.save(row);

        // Confirm availability timestamp only — never write offer qty into listings.stock.
        listing.setAvailabilityConfirmedAt(now);
        listings.save(listing);

        writeEvent(requestId, row.getId(), offer.getId(), seller.getId(), "OFFER_CREATED", Map.of(
                "unitPrice", offer.getUnitPrice().toPlainString(),
                "availableQty", offer.getAvailableQty(),
                "latencyMs", row.getResponseLatencyMs() == null ? 0 : row.getResponseLatencyMs()));
        if (request.getStatus() == ProductRequestStatus.OPEN
                || request.getStatus() == ProductRequestStatus.AWAITING_OFFERS) {
            transition(request, ProductRequestStatus.OFFERS_READY, seller.getId(), "OFFERS_READY", Map.of(
                    "offerId", offer.getId()));
        } else {
            request.setUpdatedAt(now);
            requests.save(request);
        }

        notifications.notifyCustomerOfOffer(request, offer);
        return offer;
    }

    @Transactional
    public Offer selectOffer(AppUser buyer, String offerId) {
        Offer offer = offers.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        ProductRequest request = requireRequest(offer.getRequestId());
        if (!request.getBuyerId().equals(buyer.getId()) && buyer.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        enforceExpiry(request);
        expireOffersForRequest(request.getId());
        offer = offers.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found"));
        if (offer.getStatus() == OfferStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer has expired");
        }
        if (offer.getStatus() != OfferStatus.ACTIVE && offer.getStatus() != OfferStatus.SELECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer is not selectable");
        }
        if (request.getStatus() == ProductRequestStatus.SELECTED && offer.getStatus() == OfferStatus.SELECTED) {
            return offer;
        }
        if (!request.getStatus().isActive() || request.getStatus() == ProductRequestStatus.ORDERED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request cannot accept a selection");
        }

        Instant now = Instant.now();
        for (Offer other : offers.findByRequestIdOrderByCreatedAtAsc(request.getId())) {
            if (other.getId().equals(offer.getId())) {
                other.setStatus(OfferStatus.SELECTED);
            } else if (other.getStatus() == OfferStatus.ACTIVE) {
                other.setStatus(OfferStatus.REJECTED);
            }
            offers.save(other);
        }
        transition(request, ProductRequestStatus.SELECTED, buyer.getId(), "OFFER_SELECTED", Map.of(
                "offerId", offer.getId(),
                "shopId", offer.getShopId()));
        return offers.findById(offerId).orElse(offer);
    }

    /** Select-then-order in one path used by OrderController. */
    @Transactional
    public Offer ensureSelectedForOrder(String buyerId, String requestId, String offerId) {
        ProductRequest request = requireRequest(requestId);
        if (!request.getBuyerId().equals(buyerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        enforceExpiry(request);
        Offer offer = offers.findById(offerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer not found"));
        if (!Objects.equals(offer.getRequestId(), requestId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Offer does not belong to request");
        }
        if (offer.getStatus() != OfferStatus.SELECTED) {
            AppUser buyer = new AppUser();
            buyer.setId(buyerId);
            buyer.setRole(Role.BUYER);
            offer = selectOffer(buyer, offerId);
        }
        ProductRequest latest = requireRequest(requestId);
        if (latest.getStatus() != ProductRequestStatus.ORDERED) {
            transition(latest, ProductRequestStatus.ORDERED, buyerId, "REQUEST_ORDERED", Map.of(
                    "offerId", offerId));
        }
        return offer;
    }

    @Transactional
    public void advanceDueWaves() {
        advanceDueWavesInternal();
    }

    @Transactional
    public void expireStale() {
        Instant now = Instant.now();
        for (ProductRequest request : requests.findByStatusInAndExpiresAtBefore(
                EnumSet.of(
                        ProductRequestStatus.OPEN,
                        ProductRequestStatus.AWAITING_OFFERS,
                        ProductRequestStatus.OFFERS_READY,
                        ProductRequestStatus.SELECTED),
                now)) {
            expireRequest(request, now);
        }
        for (Offer offer :
                offers.findByStatusInAndExpiresAtBefore(EnumSet.of(OfferStatus.ACTIVE), now)) {
            offer.setStatus(OfferStatus.EXPIRED);
            offers.save(offer);
        }
    }

    private void advanceDueWavesInternal() {
        PlatformSettings config = settings.findById("default").orElseThrow();
        int windowSeconds = Math.max(30, config.getRequestResponseWindowSeconds());
        int maxWaves = Math.max(1, config.getRequestMaxWaves());
        Instant now = Instant.now();

        for (ProductRequest request : requests.findAll()) {
            if (!request.getStatus().acceptsMerchantResponse()) {
                continue;
            }
            if (request.getExpiresAt().isBefore(now)) {
                expireRequest(request, now);
                continue;
            }
            maybeAdvanceWaves(request, config, windowSeconds, maxWaves, now);
        }
    }

    private void maybeAdvanceWaves(ProductRequest request) {
        PlatformSettings config = settings.findById("default").orElseThrow();
        maybeAdvanceWaves(
                request,
                config,
                Math.max(30, config.getRequestResponseWindowSeconds()),
                Math.max(1, config.getRequestMaxWaves()),
                Instant.now());
    }

    private void maybeAdvanceWaves(
            ProductRequest request,
            PlatformSettings config,
            int windowSeconds,
            int maxWaves,
            Instant now) {
        if (!request.getStatus().acceptsMerchantResponse()) {
            return;
        }
        long activeOffers = offers.countByRequestIdAndStatus(request.getId(), OfferStatus.ACTIVE)
                + offers.countByRequestIdAndStatus(request.getId(), OfferStatus.SELECTED);
        if (activeOffers > 0) {
            return;
        }
        List<RequestShop> rows = requestShops.findByRequestIdOrderByRankScoreDesc(request.getId());
        int currentWave = request.getWaveIndex();
        Instant waveStarted = rows.stream()
                .filter(r -> r.getWaveIndex() == currentWave && r.getNotifiedAt() != null)
                .map(RequestShop::getNotifiedAt)
                .min(Instant::compareTo)
                .orElse(null);
        if (waveStarted == null) {
            return;
        }
        if (waveStarted.plusSeconds(windowSeconds).isAfter(now)) {
            return;
        }

        for (RequestShop row : rows) {
            if (row.getWaveIndex() == currentWave
                    && (row.getStatus() == RequestShopStatus.NOTIFIED
                            || row.getStatus() == RequestShopStatus.VIEWED)) {
                row.setStatus(RequestShopStatus.NO_REPLY);
                requestShops.save(row);
                writeEvent(request.getId(), row.getId(), null, null, "NO_REPLY", Map.of("waveIndex", currentWave));
            }
        }

        int nextWave = currentWave + 1;
        if (nextWave >= maxWaves) {
            return;
        }
        List<RequestShop> next = rows.stream()
                .filter(r -> r.getWaveIndex() == nextWave && r.getStatus() == RequestShopStatus.QUEUED)
                .toList();
        if (next.isEmpty()) {
            return;
        }
        for (RequestShop row : next) {
            row.setStatus(RequestShopStatus.NOTIFIED);
            row.setNotifiedAt(now);
            requestShops.save(row);
            notifications.notifyMerchantOfRequest(request, row);
        }
        request.setWaveIndex(nextWave);
        request.setUpdatedAt(now);
        requests.save(request);
        writeEvent(request.getId(), null, null, null, "WAVE_NOTIFIED", Map.of(
                "waveIndex", nextWave,
                "shopCount", next.size(),
                "responseWindowSeconds", config.getRequestResponseWindowSeconds()));
    }

    private void enforceExpiry(ProductRequest request) {
        Instant now = Instant.now();
        if (request.getExpiresAt().isBefore(now) && request.getStatus().isActive()) {
            expireRequest(request, now);
        }
        expireOffersForRequest(request.getId());
    }

    private void expireRequest(ProductRequest request, Instant now) {
        if (request.getStatus().isTerminal()) {
            return;
        }
        request.setStatus(ProductRequestStatus.EXPIRED);
        request.setUpdatedAt(now);
        requests.save(request);
        for (RequestShop row : requestShops.findByRequestIdOrderByRankScoreDesc(request.getId())) {
            if (row.getStatus() == RequestShopStatus.NOTIFIED
                    || row.getStatus() == RequestShopStatus.VIEWED
                    || row.getStatus() == RequestShopStatus.QUEUED) {
                row.setStatus(RequestShopStatus.NO_REPLY);
                requestShops.save(row);
            }
        }
        for (Offer offer : offers.findByRequestIdAndStatus(request.getId(), OfferStatus.ACTIVE)) {
            offer.setStatus(OfferStatus.EXPIRED);
            offers.save(offer);
        }
        writeEvent(request.getId(), null, null, null, "REQUEST_EXPIRED", Map.of());
    }

    private void expireOffersForRequest(String requestId) {
        Instant now = Instant.now();
        for (Offer offer : offers.findByRequestIdAndStatus(requestId, OfferStatus.ACTIVE)) {
            if (offer.getExpiresAt().isBefore(now)) {
                offer.setStatus(OfferStatus.EXPIRED);
                offers.save(offer);
            }
        }
    }

    private void transition(
            ProductRequest request,
            ProductRequestStatus next,
            String actorUserId,
            String eventType,
            Map<String, Object> payload) {
        request.setStatus(next);
        request.setUpdatedAt(Instant.now());
        requests.save(request);
        writeEvent(request.getId(), null, null, actorUserId, eventType, payload);
    }

    private void writeEvent(
            String requestId,
            String requestShopId,
            String offerId,
            String actorUserId,
            String eventType,
            Map<String, Object> payload) {
        RequestEvent event = new RequestEvent();
        event.setId(Ids.next("revt"));
        event.setRequestId(requestId);
        event.setRequestShopId(requestShopId);
        event.setOfferId(offerId);
        event.setActorUserId(actorUserId);
        event.setEventType(eventType);
        event.setPayload(payload == null || payload.isEmpty() ? null : new HashMap<>(payload));
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    private ProductRequest requireRequest(String id) {
        return requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found"));
    }

    private List<Shop> ownedShops(AppUser seller) {
        if (seller.getRole() == Role.ADMIN) {
            return shops.findAll();
        }
        List<Shop> owned = new ArrayList<>(shops.findByOwnerUserId(seller.getId()));
        if (seller.getShopId() != null && !seller.getShopId().isBlank()) {
            shops.findById(seller.getShopId()).ifPresent(shop -> {
                if (owned.stream().noneMatch(s -> s.getId().equals(shop.getId()))) {
                    owned.add(shop);
                }
            });
        }
        return owned;
    }

    private RequestShop findOwnedRequestShop(AppUser seller, String requestId) {
        List<Shop> owned = ownedShops(seller);
        for (Shop shop : owned) {
            var match = requestShops.findByRequestIdAndShopId(requestId, shop.getId());
            if (match.isPresent()) {
                return match.get();
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not authorized for this request");
    }

    private String resolveListingId(ProductRequest request, RequestShop row, String inputListingId) {
        if (inputListingId != null && !inputListingId.isBlank()) {
            return inputListingId.trim();
        }
        if (row.getListingId() != null && !row.getListingId().isBlank()) {
            return row.getListingId();
        }
        if (request.getListingId() != null && !request.getListingId().isBlank()) {
            return request.getListingId();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "listingId is required");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
