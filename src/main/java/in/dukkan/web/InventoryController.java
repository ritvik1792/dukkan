package in.dukkan.web;

import in.dukkan.common.Ids;
import in.dukkan.domain.AppUser;
import in.dukkan.domain.ApprovalStatus;
import in.dukkan.domain.CatalogProduct;
import in.dukkan.domain.Listing;
import in.dukkan.domain.ListingTag;
import in.dukkan.domain.Role;
import in.dukkan.domain.Shop;
import in.dukkan.domain.TagKind;
import in.dukkan.repository.CatalogProductRepository;
import in.dukkan.repository.ListingRepository;
import in.dukkan.repository.ShopRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class InventoryController {

    public record CatalogWriteRequest(
            @NotBlank String name,
            String brand,
            @NotBlank String categoryId,
            String description,
            String unit,
            String imageLabel,
            Integer imageHue,
            String imageUrl,
            List<String> galleryUrls) {}

    public record TagWrite(String id, String label, TagKind kind, String code, Integer discountPercent) {}

    public record ListingWriteRequest(
            String catalogProductId,
            String shopId,
            BigDecimal basePrice,
            BigDecimal sellerPrice,
            Integer stock,
            Integer moq,
            String color,
            String quality,
            String warranty,
            ApprovalStatus status,
            List<TagWrite> tags) {}

    private final CatalogProductRepository catalog;
    private final ListingRepository listings;
    private final ShopRepository shops;
    private final Access access;

    public InventoryController(
            CatalogProductRepository catalog,
            ListingRepository listings,
            ShopRepository shops,
            Access access) {
        this.catalog = catalog;
        this.listings = listings;
        this.shops = shops;
        this.access = access;
    }

    @PostMapping("/catalog")
    @Transactional
    public CatalogProduct createCatalog(Authentication auth, @Valid @RequestBody CatalogWriteRequest request) {
        access.requireUser(auth);
        CatalogProduct product = new CatalogProduct();
        product.setId(Ids.next("cat"));
        apply(product, request);
        return catalog.save(product);
    }

    @PutMapping("/catalog/{id}")
    @Transactional
    public CatalogProduct updateCatalog(
            Authentication auth, @PathVariable String id, @Valid @RequestBody CatalogWriteRequest request) {
        access.requireUser(auth);
        CatalogProduct product = catalog.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        apply(product, request);
        return catalog.save(product);
    }

    @PostMapping("/listings")
    @Transactional
    public Listing createListing(Authentication auth, @Valid @RequestBody ListingWriteRequest request) {
        AppUser user = access.requireUser(auth);
        if (request.catalogProductId() == null || request.shopId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "catalogProductId and shopId are required");
        }
        assertCanEditShop(user, request.shopId());
        Listing listing = new Listing();
        listing.setId(Ids.next("l"));
        listing.setCatalogProductId(request.catalogProductId());
        listing.setShopId(request.shopId());
        apply(listing, request);
        return listings.save(listing);
    }

    @PutMapping("/listings/{id}")
    @Transactional
    public Listing updateListing(
            Authentication auth, @PathVariable String id, @Valid @RequestBody ListingWriteRequest request) {
        AppUser user = access.requireUser(auth);
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanEditShop(user, listing.getShopId());
        apply(listing, request);
        return listings.save(listing);
    }

    @DeleteMapping("/listings/{id}")
    @Transactional
    public void deleteListing(Authentication auth, @PathVariable String id) {
        AppUser user = access.requireUser(auth);
        Listing listing = listings.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        assertCanEditShop(user, listing.getShopId());
        listings.delete(listing);
    }

    private void apply(CatalogProduct product, CatalogWriteRequest request) {
        product.setName(request.name().trim());
        product.setBrand(request.brand() == null || request.brand().isBlank() ? "Unbranded" : request.brand().trim());
        product.setCategoryId(request.categoryId());
        product.setDescription(request.description());
        product.setUnit(request.unit() == null || request.unit().isBlank() ? "1 pc" : request.unit());
        product.setImageLabel(request.imageLabel() == null || request.imageLabel().isBlank()
                ? product.getName().substring(0, Math.min(8, product.getName().length()))
                : request.imageLabel());
        product.setImageHue(request.imageHue() == null ? 32 : request.imageHue());
        product.setImageUrl(blankToNull(request.imageUrl()));
        product.getGalleryUrls().clear();
        if (request.galleryUrls() != null) {
            product.getGalleryUrls().addAll(request.galleryUrls());
        }
    }

    private void apply(Listing listing, ListingWriteRequest request) {
        if (request.basePrice() != null) {
            listing.setBasePrice(request.basePrice());
        }
        if (request.sellerPrice() != null) {
            listing.setSellerPrice(request.sellerPrice());
        }
        if (request.stock() != null) {
            listing.setStock(request.stock());
        }
        if (request.moq() != null) {
            listing.setMoq(request.moq());
        }
        if (request.color() != null) {
            listing.setColor(blankToNull(request.color()));
        }
        if (request.quality() != null) {
            listing.setQuality(blankToNull(request.quality()));
        }
        if (request.warranty() != null) {
            listing.setWarranty(blankToNull(request.warranty()));
        }
        listing.setStatus(request.status() == null ? ApprovalStatus.APPROVED : request.status());
        if (request.tags() != null) {
            listing.getTags().clear();
            for (TagWrite tag : request.tags()) {
                ListingTag item = new ListingTag();
                item.setId(tag.id() == null || tag.id().isBlank() ? Ids.next("tag") : tag.id());
                item.setListing(listing);
                item.setLabel(tag.label());
                item.setKind(tag.kind() == null ? TagKind.BADGE : tag.kind());
                item.setCode(tag.code());
                item.setDiscountPercent(tag.discountPercent());
                listing.getTags().add(item);
            }
        }
        if (listing.getMoq() == 0) {
            listing.setMoq(1);
        }
        if (listing.getBasePrice() == null) {
            listing.setBasePrice(BigDecimal.ZERO);
        }
        if (listing.getSellerPrice() == null) {
            listing.setSellerPrice(listing.getBasePrice());
        }
    }

    private void assertCanEditShop(AppUser user, String shopId) {
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Shop not found"));
        if (!shop.getOwnerUserId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
