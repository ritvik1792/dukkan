package in.dukkan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "request_shops")
public class RequestShop {

    @Id
    private String id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Column(name = "listing_id")
    private String listingId;

    @Column(name = "wave_index", nullable = false)
    private int waveIndex;

    @Column(name = "rank_score", nullable = false)
    private BigDecimal rankScore = BigDecimal.ZERO;

    @Column(name = "distance_km")
    private BigDecimal distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestShopStatus status;

    @Column(name = "notified_at")
    private Instant notifiedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "response_latency_ms")
    private Long responseLatencyMs;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getListingId() {
        return listingId;
    }

    public void setListingId(String listingId) {
        this.listingId = listingId;
    }

    public int getWaveIndex() {
        return waveIndex;
    }

    public void setWaveIndex(int waveIndex) {
        this.waveIndex = waveIndex;
    }

    public BigDecimal getRankScore() {
        return rankScore;
    }

    public void setRankScore(BigDecimal rankScore) {
        this.rankScore = rankScore;
    }

    public BigDecimal getDistanceKm() {
        return distanceKm;
    }

    public void setDistanceKm(BigDecimal distanceKm) {
        this.distanceKm = distanceKm;
    }

    public RequestShopStatus getStatus() {
        return status;
    }

    public void setStatus(RequestShopStatus status) {
        this.status = status;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Long getResponseLatencyMs() {
        return responseLatencyMs;
    }

    public void setResponseLatencyMs(Long responseLatencyMs) {
        this.responseLatencyMs = responseLatencyMs;
    }
}
