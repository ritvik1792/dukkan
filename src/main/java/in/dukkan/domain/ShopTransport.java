package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_transport")
public class ShopTransport {

    @Id
    private String id;

    @JsonIgnore
    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShopTransportKind kind = ShopTransportKind.BIKE;

    @Column(nullable = false)
    private String label;

    private String registration;

    @Column(name = "capacity_kg")
    private Integer capacityKg;

    @Column(nullable = false)
    private boolean available = true;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public ShopTransportKind getKind() {
        return kind;
    }

    public void setKind(ShopTransportKind kind) {
        this.kind = kind;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRegistration() {
        return registration;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public Integer getCapacityKg() {
        return capacityKg;
    }

    public void setCapacityKg(Integer capacityKg) {
        this.capacityKg = capacityKg;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
