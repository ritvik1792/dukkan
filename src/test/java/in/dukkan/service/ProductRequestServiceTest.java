package in.dukkan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.dukkan.domain.AppUser;
import in.dukkan.domain.ApprovalStatus;
import in.dukkan.domain.Listing;
import in.dukkan.domain.Offer;
import in.dukkan.domain.OfferStatus;
import in.dukkan.domain.PlatformSettings;
import in.dukkan.domain.ProductRequest;
import in.dukkan.domain.ProductRequestStatus;
import in.dukkan.domain.RequestShop;
import in.dukkan.domain.RequestShopStatus;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopStatus;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.OfferRepository;
import in.dukkan.repository.ProductRequestRepository;
import in.dukkan.repository.RequestEventRepository;
import in.dukkan.repository.RequestShopRepository;
import in.dukkan.repository.SettingsRepository;
import in.dukkan.repository.ShopRepository;
import in.dukkan.service.ProductRequestService.CreateRequestInput;
import in.dukkan.service.ProductRequestService.MerchantRespondInput;
import in.dukkan.service.RequestParser.ParsedRequest;
import in.dukkan.service.ShopMatcher.RankedCandidate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductRequestServiceTest {

    @Mock ProductRequestRepository requests;
    @Mock RequestShopRepository requestShops;
    @Mock OfferRepository offers;
    @Mock RequestEventRepository events;
    @Mock ListingRepository listings;
    @Mock ShopRepository shops;
    @Mock SettingsRepository settings;
    @Mock RequestParser requestParser;
    @Mock ShopMatcher matching;
    @Mock NotificationService notifications;

    ProductRequestService service;

    AppUser buyer;
    AppUser seller;
    AppUser otherSeller;
    PlatformSettings config;
    Shop shopA;
    Shop shopB;
    Listing listingA;
    Listing listingB;

    @BeforeEach
    void setUp() {
        service = new ProductRequestService(
                requests,
                requestShops,
                offers,
                events,
                listings,
                shops,
                settings,
                requestParser,
                matching,
                notifications);

        buyer = user("buyer-1", Role.BUYER, null);
        seller = user("seller-1", Role.SELLER, "shop-a");
        otherSeller = user("seller-2", Role.SELLER, "shop-b");

        config = new PlatformSettings();
        config.setDeliveryRadiusKm(5);
        config.setPartnerEtaMinutes(30);
        config.setRequestResponseWindowSeconds(120);
        config.setRequestWaveSize(5);
        config.setRequestMaxShops(20);
        config.setOfferExpirySeconds(900);
        config.setRequestMaxWaves(3);
        when(settings.findById("default")).thenReturn(Optional.of(config));

        shopA = shop("shop-a", "seller-1", 28.61, 77.21);
        shopB = shop("shop-b", "seller-2", 28.62, 77.22);
        listingA = listing("lst-a", "shop-a", "cat-1");
        listingB = listing("lst-b", "shop-b", "cat-1");

        when(requestParser.parse(any(), any(), any()))
                .thenReturn(new ParsedRequest("cat-1", "lst-a", "Test product"));
        when(requests.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestShops.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(requestShops.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(offers.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(events.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(listings.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(shops.findByOwnerUserId("seller-1")).thenReturn(List.of(shopA));
        when(shops.findByOwnerUserId("seller-2")).thenReturn(List.of(shopB));
        when(shops.findById("shop-a")).thenReturn(Optional.of(shopA));
        when(shops.findById("shop-b")).thenReturn(Optional.of(shopB));
        when(listings.findById("lst-a")).thenReturn(Optional.of(listingA));
        when(listings.findById("lst-b")).thenReturn(Optional.of(listingB));
    }

    @Test
    void createRequiresCatalogProductAndCoordinates() {
        when(requestParser.parse(any(), any(), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "catalogProductId is required"));
        assertThrows(
                ResponseStatusException.class,
                () -> service.create(buyer, new CreateRequestInput(null, null, null, 28.6, 77.2, null)));
        assertThrows(
                ResponseStatusException.class,
                () -> service.create(buyer, new CreateRequestInput("cat-1", null, null, null, 77.2, null)));
    }

    @Test
    void createPersistsOptionalMaxBudget() {
        when(matching.findCandidates(anyString(), any(Double.class), any(Double.class)))
                .thenReturn(List.of(new RankedCandidate(shopA, listingA, 0.5, 0.9)));

        ProductRequest created = service.create(
                buyer,
                new CreateRequestInput("cat-1", "lst-a", null, 28.6, 77.2, new BigDecimal("250.00")));

        assertEquals(new BigDecimal("250.00"), created.getMaxBudget());
        assertEquals(ProductRequestStatus.AWAITING_OFFERS, created.getStatus());
    }

    @Test
    void createRejectsNonPositiveMaxBudget() {
        assertThrows(
                ResponseStatusException.class,
                () -> service.create(
                        buyer, new CreateRequestInput("cat-1", "lst-a", null, 28.6, 77.2, BigDecimal.ZERO)));
    }

    @Test
    void createNotifiesFirstWaveOnly() {
        when(matching.findCandidates(anyString(), any(Double.class), any(Double.class)))
                .thenReturn(List.of(
                        new RankedCandidate(shopA, listingA, 0.5, 0.9),
                        new RankedCandidate(shopB, listingB, 0.8, 0.8)));
        config.setRequestWaveSize(1);

        ProductRequest created = service.create(buyer, new CreateRequestInput("cat-1", "lst-a", null, 28.6, 77.2, null));

        assertEquals(ProductRequestStatus.AWAITING_OFFERS, created.getStatus());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<RequestShop>> captor = ArgumentCaptor.forClass(List.class);
        verify(requestShops).saveAll(captor.capture());
        List<RequestShop> rows = captor.getValue();
        assertEquals(2, rows.size());
        assertEquals(RequestShopStatus.NOTIFIED, rows.get(0).getStatus());
        assertEquals(RequestShopStatus.QUEUED, rows.get(1).getStatus());
        verify(notifications, atLeastOnce()).notifyMerchantOfRequest(any(), any());
    }

    @Test
    void merchantAcceptCreatesTemporaryOfferWithoutChangingStock() {
        ProductRequest request = activeRequest("req-1");
        RequestShop row = notifiedRow("rs-1", "req-1", "shop-a", "lst-a");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-1", "shop-a")).thenReturn(Optional.of(row));
        when(offers.countByRequestIdAndStatus(anyString(), any())).thenReturn(0L);
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-1")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        int stockBefore = listingA.getStock();
        Offer offer = service.respond(
                seller,
                "req-1",
                new MerchantRespondInput("ACCEPTED", new BigDecimal("99.50"), 3, "lst-a", "Ready"));

        assertNotNull(offer);
        assertEquals(OfferStatus.ACTIVE, offer.getStatus());
        assertEquals(new BigDecimal("99.50"), offer.getUnitPrice());
        assertEquals(3, offer.getAvailableQty());
        assertEquals(stockBefore, listingA.getStock());
        assertNotNull(listingA.getAvailabilityConfirmedAt());
        assertEquals(RequestShopStatus.ACCEPTED, row.getStatus());
        assertNotNull(row.getResponseLatencyMs());
        assertEquals(ProductRequestStatus.OFFERS_READY, request.getStatus());
        verify(notifications).notifyCustomerOfOffer(any(), any());
    }

    @Test
    void merchantRejectRecordsDeclined() {
        ProductRequest request = activeRequest("req-2");
        RequestShop row = notifiedRow("rs-2", "req-2", "shop-a", "lst-a");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-2", "shop-a")).thenReturn(Optional.of(row));
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-2")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        Offer offer = service.respond(seller, "req-2", new MerchantRespondInput("REJECTED", null, null, null, null));
        assertNull(offer);
        assertEquals(RequestShopStatus.DECLINED, row.getStatus());
        assertNotNull(row.getResponseLatencyMs());
    }

    @Test
    void unauthorizedMerchantCannotRespond() {
        ProductRequest request = activeRequest("req-3");
        RequestShop row = notifiedRow("rs-3", "req-3", "shop-a", "lst-a");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-3", "shop-a")).thenReturn(Optional.of(row));
        when(requestShops.findByRequestIdAndShopId("req-3", "shop-b")).thenReturn(Optional.empty());
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-3")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        assertThrows(
                ResponseStatusException.class,
                () -> service.respond(
                        otherSeller,
                        "req-3",
                        new MerchantRespondInput("ACCEPTED", new BigDecimal("10"), 1, "lst-a", null)));
    }

    @Test
    void expiredRequestRejectsMerchantResponse() {
        ProductRequest request = activeRequest("req-exp");
        request.setExpiresAt(Instant.now().minusSeconds(10));
        RequestShop row = notifiedRow("rs-exp", "req-exp", "shop-a", "lst-a");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-exp", "shop-a")).thenReturn(Optional.of(row));
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-exp")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        assertThrows(
                ResponseStatusException.class,
                () -> service.respond(
                        seller,
                        "req-exp",
                        new MerchantRespondInput("ACCEPTED", new BigDecimal("10"), 1, "lst-a", null)));
        assertEquals(ProductRequestStatus.EXPIRED, request.getStatus());
    }

    @Test
    void duplicateResponseFromSameShopRejected() {
        ProductRequest request = activeRequest("req-dup");
        RequestShop row = notifiedRow("rs-dup", "req-dup", "shop-a", "lst-a");
        row.setStatus(RequestShopStatus.ACCEPTED);
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-dup", "shop-a")).thenReturn(Optional.of(row));
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-dup")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.respond(
                        seller,
                        "req-dup",
                        new MerchantRespondInput("ACCEPTED", new BigDecimal("10"), 1, "lst-a", null)));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void twoShopsCanBothCreateOffers() {
        ProductRequest request = activeRequest("req-multi");
        RequestShop rowA = notifiedRow("rs-a", "req-multi", "shop-a", "lst-a");
        RequestShop rowB = notifiedRow("rs-b", "req-multi", "shop-b", "lst-b");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-multi", "shop-a")).thenReturn(Optional.of(rowA));
        when(requestShops.findByRequestIdAndShopId("req-multi", "shop-b")).thenReturn(Optional.of(rowB));
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-multi")).thenReturn(List.of(rowA, rowB));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());
        when(offers.countByRequestIdAndStatus(anyString(), any())).thenReturn(0L);

        Offer offerA = service.respond(
                seller,
                "req-multi",
                new MerchantRespondInput("ACCEPTED", new BigDecimal("11"), 2, "lst-a", null));
        Offer offerB = service.respond(
                otherSeller,
                "req-multi",
                new MerchantRespondInput("ACCEPTED", new BigDecimal("12"), 1, "lst-b", null));

        assertNotNull(offerA);
        assertNotNull(offerB);
        assertEquals(RequestShopStatus.ACCEPTED, rowA.getStatus());
        assertEquals(RequestShopStatus.ACCEPTED, rowB.getStatus());
    }

    @Test
    void customerSelectMarksOneSelectedAndOthersRejected() {
        ProductRequest request = activeRequest("req-sel");
        request.setStatus(ProductRequestStatus.OFFERS_READY);
        Offer offerA = offer("off-a", "req-sel", "shop-a", "lst-a", OfferStatus.ACTIVE);
        Offer offerB = offer("off-b", "req-sel", "shop-b", "lst-b", OfferStatus.ACTIVE);
        AtomicReference<List<Offer>> store = new AtomicReference<>(new ArrayList<>(List.of(offerA, offerB)));

        stubRequestLookup(request);
        when(offers.findById("off-a")).thenAnswer(inv -> store.get().stream()
                .filter(o -> o.getId().equals("off-a"))
                .findFirst());
        when(offers.findByRequestIdOrderByCreatedAtAsc("req-sel")).thenAnswer(inv -> store.get());
        when(offers.findByRequestIdAndStatus("req-sel", OfferStatus.ACTIVE)).thenAnswer(inv -> store.get().stream()
                .filter(o -> o.getStatus() == OfferStatus.ACTIVE)
                .toList());
        when(offers.save(any())).thenAnswer(inv -> {
            Offer saved = inv.getArgument(0);
            List<Offer> next = new ArrayList<>();
            for (Offer o : store.get()) {
                next.add(o.getId().equals(saved.getId()) ? saved : o);
            }
            store.set(next);
            return saved;
        });
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-sel")).thenReturn(List.of());

        Offer selected = service.selectOffer(buyer, "off-a");
        assertEquals(OfferStatus.SELECTED, selected.getStatus());
        assertEquals(ProductRequestStatus.SELECTED, request.getStatus());
        assertEquals(OfferStatus.REJECTED, store.get().stream()
                .filter(o -> o.getId().equals("off-b"))
                .findFirst()
                .orElseThrow()
                .getStatus());
    }

    @Test
    void acceptRequiresPrice() {
        ProductRequest request = activeRequest("req-price");
        RequestShop row = notifiedRow("rs-price", "req-price", "shop-a", "lst-a");
        stubRequestLookup(request);
        when(requestShops.findByRequestIdAndShopId("req-price", "shop-a")).thenReturn(Optional.of(row));
        when(requestShops.findByRequestIdOrderByRankScoreDesc("req-price")).thenReturn(List.of(row));
        when(offers.findByRequestIdAndStatus(anyString(), any())).thenReturn(List.of());

        assertThrows(
                ResponseStatusException.class,
                () -> service.respond(seller, "req-price", new MerchantRespondInput("ACCEPTED", null, 1, "lst-a", null)));
        verify(notifications, never()).notifyCustomerOfOffer(any(), any());
    }

    private void stubRequestLookup(ProductRequest request) {
        when(requests.findById(request.getId())).thenReturn(Optional.of(request));
    }

    private static AppUser user(String id, Role role, String shopId) {
        AppUser u = new AppUser();
        u.setId(id);
        u.setRole(role);
        u.setShopId(shopId);
        u.setName(id);
        u.setEmail(id + "@test.local");
        return u;
    }

    private static Shop shop(String id, String owner, double lat, double lng) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setOwnerUserId(owner);
        shop.setName(id);
        shop.setAddress("addr");
        shop.setLat(lat);
        shop.setLng(lng);
        shop.setStatus(ShopStatus.ACTIVE);
        shop.setRating(BigDecimal.valueOf(4.5));
        shop.setNotificationsEnabled(true);
        shop.setNotifyStockConfirmation(true);
        return shop;
    }

    private static Listing listing(String id, String shopId, String catalogId) {
        Listing listing = new Listing();
        listing.setId(id);
        listing.setShopId(shopId);
        listing.setCatalogProductId(catalogId);
        listing.setBasePrice(new BigDecimal("100"));
        listing.setSellerPrice(new BigDecimal("90"));
        listing.setStock(10);
        listing.setMoq(1);
        listing.setStatus(ApprovalStatus.APPROVED);
        return listing;
    }

    private static ProductRequest activeRequest(String id) {
        ProductRequest request = new ProductRequest();
        request.setId(id);
        request.setBuyerId("buyer-1");
        request.setCatalogProductId("cat-1");
        request.setListingId("lst-a");
        request.setBuyerLat(28.6);
        request.setBuyerLng(77.2);
        request.setStatus(ProductRequestStatus.AWAITING_OFFERS);
        request.setWaveIndex(0);
        Instant now = Instant.now();
        request.setCreatedAt(now);
        request.setUpdatedAt(now);
        request.setExpiresAt(now.plusSeconds(900));
        return request;
    }

    private static RequestShop notifiedRow(String id, String requestId, String shopId, String listingId) {
        RequestShop row = new RequestShop();
        row.setId(id);
        row.setRequestId(requestId);
        row.setShopId(shopId);
        row.setListingId(listingId);
        row.setWaveIndex(0);
        row.setRankScore(BigDecimal.ONE);
        row.setDistanceKm(BigDecimal.valueOf(0.5));
        row.setStatus(RequestShopStatus.NOTIFIED);
        row.setNotifiedAt(Instant.now().minusSeconds(5));
        return row;
    }

    private static Offer offer(String id, String requestId, String shopId, String listingId, OfferStatus status) {
        Offer offer = new Offer();
        offer.setId(id);
        offer.setRequestId(requestId);
        offer.setRequestShopId("rs-" + id);
        offer.setShopId(shopId);
        offer.setListingId(listingId);
        offer.setUnitPrice(new BigDecimal("50"));
        offer.setAvailableQty(1);
        offer.setStatus(status);
        Instant now = Instant.now();
        offer.setCreatedAt(now);
        offer.setExpiresAt(now.plusSeconds(900));
        return offer;
    }
}
