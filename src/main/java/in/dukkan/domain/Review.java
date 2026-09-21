package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    private String id;

    @Column(name = "catalog_product_id", nullable = false)
    private String catalogProductId;

    @Column(name = "listing_id")
    private String listingId;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(name = "buyer_id", nullable = false)
    private String buyerId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "order_id")
    private String orderId;

    @Column(nullable = false)
    private boolean hidden;

    @JsonIgnore
    @Column(name = "seller_reply")
    private String sellerReplyText;

    @JsonIgnore
    @Column(name = "seller_replied_at")
    private Instant sellerRepliedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "review_images", joinColumns = @JoinColumn(name = "review_id"))
    @Column(name = "url")
    @OrderColumn(name = "sort_order")
    private List<String> imageUrls = new ArrayList<>();

    public record SellerReplyView(String body, Instant createdAt) {}

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

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getBuyerId() {
        return buyerId;
    }

    public void setBuyerId(String buyerId) {
        this.buyerId = buyerId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSellerReplyText() {
        return sellerReplyText;
    }

    public void setSellerReplyText(String sellerReplyText) {
        this.sellerReplyText = sellerReplyText;
    }

    public Instant getSellerRepliedAt() {
        return sellerRepliedAt;
    }

    public void setSellerRepliedAt(Instant sellerRepliedAt) {
        this.sellerRepliedAt = sellerRepliedAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    @JsonProperty("sellerReply")
    public SellerReplyView getSellerReply() {
        if (sellerReplyText == null || sellerReplyText.isBlank()) {
            return null;
        }
        return new SellerReplyView(sellerReplyText, sellerRepliedAt);
    }
}
