package in.dukkan.service;

import in.dukkan.domain.CatalogProduct;
import in.dukkan.domain.Listing;
import in.dukkan.repository.CatalogProductRepository;
import in.dukkan.repository.ListingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogProductRequestParser implements RequestParser {

    private final CatalogProductRepository catalog;
    private final ListingRepository listings;

    public CatalogProductRequestParser(CatalogProductRepository catalog, ListingRepository listings) {
        this.catalog = catalog;
        this.listings = listings;
    }

    @Override
    public ParsedRequest parse(String catalogProductId, String listingId, String queryText) {
        if (catalogProductId == null || catalogProductId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalogProductId is required");
        }
        CatalogProduct product = catalog
                .findById(catalogProductId.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog product not found"));
        String resolvedListing = null;
        if (listingId != null && !listingId.isBlank()) {
            Listing listing = listings
                    .findById(listingId.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Listing not found"));
            if (!listing.getCatalogProductId().equals(product.getId())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Listing does not match catalog product");
            }
            resolvedListing = listing.getId();
        }
        String query = queryText == null || queryText.isBlank() ? product.getName() : queryText.trim();
        return new ParsedRequest(product.getId(), resolvedListing, query);
    }
}
