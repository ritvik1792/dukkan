package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "shops")
public class Shop extends AuditableEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "owner_user_id", nullable = false)
    private String ownerUserId;

    private String description;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private BigDecimal rating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(nullable = false)
    private boolean verified;

    private String gstin;

    @Column(name = "year_started")
    private Integer yearStarted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopStatus status;

    @Column(name = "partner_delivery_enabled", nullable = false)
    private boolean partnerDeliveryEnabled;

    @Column(name = "shop_delivery_enabled", nullable = false)
    private boolean shopDeliveryEnabled;

    @Column(name = "partner_delivery_fee", nullable = false)
    private BigDecimal partnerDeliveryFee;

    @Column(name = "shop_delivery_fee", nullable = false)
    private BigDecimal shopDeliveryFee;

    @Column(name = "min_order_amount", nullable = false)
    private BigDecimal minOrderAmount;

    @Column(name = "image_url")
    private String imageUrl;

    @JsonProperty("isOpen")
    @Column(name = "is_open", nullable = false)
    private boolean isOpen = true;

    @Column(name = "open_time", nullable = false)
    private String openTime = "09:00";

    @Column(name = "close_time", nullable = false)
    private String closeTime = "21:00";

    @Column(name = "notifications_enabled", nullable = false)
    private boolean notificationsEnabled = true;

    @Column(name = "notify_order_received", nullable = false)
    private boolean notifyOrderReceived = true;

    @Column(name = "notify_order_status", nullable = false)
    private boolean notifyOrderStatus = true;

    @Column(name = "notify_stock_confirmation", nullable = false)
    private boolean notifyStockConfirmation = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false)
    private ProviderType providerType = ProviderType.PRODUCT_BUSINESS;

    @Column(name = "products_allowed", nullable = false)
    private boolean productsAllowed = true;

    @Column(name = "services_allowed", nullable = false)
    private boolean servicesAllowed;

    @Column(name = "bookings_allowed", nullable = false)
    private boolean bookingsAllowed;

    @Column(name = "service_requests_allowed", nullable = false)
    private boolean serviceRequestsAllowed;

    @Column(name = "orders_allowed", nullable = false)
    private boolean ordersAllowed = true;

    @Column(name = "quick_delivery_allowed", nullable = false)
    private boolean quickDeliveryAllowed;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.UNVERIFIED;

    @Column(name = "service_area")
    private String serviceArea;

    private String profession;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shop_categories", joinColumns = @JoinColumn(name = "shop_id"))
    @Column(name = "category_id")
    private Set<String> categoryIds = new HashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public void setReviewCount(int reviewCount) {
        this.reviewCount = reviewCount;
    }

    public boolean isVerified() {
        return verified;
    }

    public void setVerified(boolean verified) {
        this.verified = verified;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }

    public Integer getYearStarted() {
        return yearStarted;
    }

    public void setYearStarted(Integer yearStarted) {
        this.yearStarted = yearStarted;
    }

    public ShopStatus getStatus() {
        return status;
    }

    public void setStatus(ShopStatus status) {
        this.status = status;
    }

    public boolean isPartnerDeliveryEnabled() {
        return partnerDeliveryEnabled;
    }

    public void setPartnerDeliveryEnabled(boolean partnerDeliveryEnabled) {
        this.partnerDeliveryEnabled = partnerDeliveryEnabled;
    }

    public boolean isShopDeliveryEnabled() {
        return shopDeliveryEnabled;
    }

    public void setShopDeliveryEnabled(boolean shopDeliveryEnabled) {
        this.shopDeliveryEnabled = shopDeliveryEnabled;
    }

    public BigDecimal getPartnerDeliveryFee() {
        return partnerDeliveryFee;
    }

    public void setPartnerDeliveryFee(BigDecimal partnerDeliveryFee) {
        this.partnerDeliveryFee = partnerDeliveryFee;
    }

    public BigDecimal getShopDeliveryFee() {
        return shopDeliveryFee;
    }

    public void setShopDeliveryFee(BigDecimal shopDeliveryFee) {
        this.shopDeliveryFee = shopDeliveryFee;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public Set<String> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(Set<String> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        this.isOpen = open;
    }

    public String getOpenTime() {
        return openTime;
    }

    public void setOpenTime(String openTime) {
        this.openTime = openTime;
    }

    public String getCloseTime() {
        return closeTime;
    }

    public void setCloseTime(String closeTime) {
        this.closeTime = closeTime;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean isNotifyOrderReceived() {
        return notifyOrderReceived;
    }

    public void setNotifyOrderReceived(boolean notifyOrderReceived) {
        this.notifyOrderReceived = notifyOrderReceived;
    }

    public boolean isNotifyOrderStatus() {
        return notifyOrderStatus;
    }

    public void setNotifyOrderStatus(boolean notifyOrderStatus) {
        this.notifyOrderStatus = notifyOrderStatus;
    }

    public boolean isNotifyStockConfirmation() {
        return notifyStockConfirmation;
    }

    public void setNotifyStockConfirmation(boolean notifyStockConfirmation) {
        this.notifyStockConfirmation = notifyStockConfirmation;
    }

    public ProviderType getProviderType() {
        return providerType;
    }

    public void setProviderType(ProviderType providerType) {
        this.providerType = providerType;
    }

    public boolean isProductsAllowed() {
        return productsAllowed;
    }

    public void setProductsAllowed(boolean productsAllowed) {
        this.productsAllowed = productsAllowed;
    }

    public boolean isServicesAllowed() {
        return servicesAllowed;
    }

    public void setServicesAllowed(boolean servicesAllowed) {
        this.servicesAllowed = servicesAllowed;
    }

    public boolean isBookingsAllowed() {
        return bookingsAllowed;
    }

    public void setBookingsAllowed(boolean bookingsAllowed) {
        this.bookingsAllowed = bookingsAllowed;
    }

    public boolean isServiceRequestsAllowed() {
        return serviceRequestsAllowed;
    }

    public void setServiceRequestsAllowed(boolean serviceRequestsAllowed) {
        this.serviceRequestsAllowed = serviceRequestsAllowed;
    }

    public boolean isOrdersAllowed() {
        return ordersAllowed;
    }

    public void setOrdersAllowed(boolean ordersAllowed) {
        this.ordersAllowed = ordersAllowed;
    }

    public boolean isQuickDeliveryAllowed() {
        return quickDeliveryAllowed;
    }

    public void setQuickDeliveryAllowed(boolean quickDeliveryAllowed) {
        this.quickDeliveryAllowed = quickDeliveryAllowed;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public void setVerificationStatus(VerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }
}
