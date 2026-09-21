package in.dukkan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_settings")
public class PlatformSettings {

    @Id
    private String id = "default";

    @Column(name = "delivery_radius_km", nullable = false)
    private int deliveryRadiusKm;

    @Column(name = "partner_eta_minutes", nullable = false)
    private int partnerEtaMinutes;

    @Column(name = "show_demo_role_switcher", nullable = false)
    private boolean showDemoRoleSwitcher;

    @Column(name = "request_response_window_seconds", nullable = false)
    private int requestResponseWindowSeconds = 120;

    @Column(name = "request_wave_size", nullable = false)
    private int requestWaveSize = 5;

    @Column(name = "request_max_shops", nullable = false)
    private int requestMaxShops = 20;

    @Column(name = "offer_expiry_seconds", nullable = false)
    private int offerExpirySeconds = 900;

    @Column(name = "request_max_waves", nullable = false)
    private int requestMaxWaves = 3;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getDeliveryRadiusKm() {
        return deliveryRadiusKm;
    }

    public void setDeliveryRadiusKm(int deliveryRadiusKm) {
        this.deliveryRadiusKm = deliveryRadiusKm;
    }

    public int getPartnerEtaMinutes() {
        return partnerEtaMinutes;
    }

    public void setPartnerEtaMinutes(int partnerEtaMinutes) {
        this.partnerEtaMinutes = partnerEtaMinutes;
    }

    public boolean isShowDemoRoleSwitcher() {
        return showDemoRoleSwitcher;
    }

    public void setShowDemoRoleSwitcher(boolean showDemoRoleSwitcher) {
        this.showDemoRoleSwitcher = showDemoRoleSwitcher;
    }

    public int getRequestResponseWindowSeconds() {
        return requestResponseWindowSeconds;
    }

    public void setRequestResponseWindowSeconds(int requestResponseWindowSeconds) {
        this.requestResponseWindowSeconds = requestResponseWindowSeconds;
    }

    public int getRequestWaveSize() {
        return requestWaveSize;
    }

    public void setRequestWaveSize(int requestWaveSize) {
        this.requestWaveSize = requestWaveSize;
    }

    public int getRequestMaxShops() {
        return requestMaxShops;
    }

    public void setRequestMaxShops(int requestMaxShops) {
        this.requestMaxShops = requestMaxShops;
    }

    public int getOfferExpirySeconds() {
        return offerExpirySeconds;
    }

    public void setOfferExpirySeconds(int offerExpirySeconds) {
        this.offerExpirySeconds = offerExpirySeconds;
    }

    public int getRequestMaxWaves() {
        return requestMaxWaves;
    }

    public void setRequestMaxWaves(int requestMaxWaves) {
        this.requestMaxWaves = requestMaxWaves;
    }
}
