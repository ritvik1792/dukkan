package in.dukkan.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listings")
public class Listing {

    @Id
    private String id;

    @Column(name = "catalog_product_id", nullable = false)
    private String catalogProductId;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(name = "base_price", nullable = false)
    private BigDecimal basePrice;

    @Column(name = "seller_price", nullable = false)
    private BigDecimal sellerPrice;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private int moq;

    private String color;

    private String quality;

    private String warranty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status;

    @Column(name = "availability_confirmed_at")
    private Instant availabilityConfirmedAt;

    @OneToMany(mappedBy = "listing", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    private List<ListingTag> tags = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCatalogProductId() {
        return catalogProductId;
    }

    public void setCatalogProductId(String catalogProductId) {
        this.catalogProductId = catalogProductId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public BigDecimal getSellerPrice() {
        return sellerPrice;
    }

    public void setSellerPrice(BigDecimal sellerPrice) {
        this.sellerPrice = sellerPrice;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public int getMoq() {
        return moq;
    }

    public void setMoq(int moq) {
        this.moq = moq;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getWarranty() {
        return warranty;
    }

    public void setWarranty(String warranty) {
        this.warranty = warranty;
    }

    public ApprovalStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalStatus status) {
        this.status = status;
    }

    public Instant getAvailabilityConfirmedAt() {
        return availabilityConfirmedAt;
    }

    public void setAvailabilityConfirmedAt(Instant availabilityConfirmedAt) {
        this.availabilityConfirmedAt = availabilityConfirmedAt;
    }

    public List<ListingTag> getTags() {
        return tags;
    }

    public void setTags(List<ListingTag> tags) {
        this.tags = tags;
    }
}
