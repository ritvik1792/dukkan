package in.dukkan.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "services")
public class ProviderService extends AuditableEntity {

    @Id
    private String id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "category_id")
    private String categoryId;

    private BigDecimal price;

    @Column(name = "starting_price")
    private BigDecimal startingPrice;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "service_area")
    private String serviceArea;

    @Column(name = "booking_enabled", nullable = false)
    private boolean bookingEnabled;

    @Column(name = "request_enabled", nullable = false)
    private boolean requestEnabled = true;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "service_images", joinColumns = @JoinColumn(name = "service_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "url", nullable = false)
    private List<String> imageUrls = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getStartingPrice() {
        return startingPrice;
    }

    public void setStartingPrice(BigDecimal startingPrice) {
        this.startingPrice = startingPrice;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getServiceArea() {
        return serviceArea;
    }

    public void setServiceArea(String serviceArea) {
        this.serviceArea = serviceArea;
    }

    public boolean isBookingEnabled() {
        return bookingEnabled;
    }

    public void setBookingEnabled(boolean bookingEnabled) {
        this.bookingEnabled = bookingEnabled;
    }

    public boolean isRequestEnabled() {
        return requestEnabled;
    }

    public void setRequestEnabled(boolean requestEnabled) {
        this.requestEnabled = requestEnabled;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public ServiceStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public void onPrePersist() {
        super.onPrePersist();
        if (this.updatedAt == null) {
            this.updatedAt = getLastUpdated();
        }
    }

    @Override
    public void onPreUpdate() {
        super.onPreUpdate();
        this.updatedAt = getLastUpdated();
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls == null ? new ArrayList<>() : imageUrls;
    }
}
