package in.dukkan.service;

import in.dukkan.domain.Listing;
import in.dukkan.domain.Shop;
import java.util.List;

public interface ShopMatcher {

    record RankedCandidate(Shop shop, Listing listing, double distanceKm, double rankScore) {}

    List<RankedCandidate> findCandidates(String catalogProductId, double buyerLat, double buyerLng);
}
