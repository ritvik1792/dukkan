package in.dukkan.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "shop_partners")
@IdClass(ShopPartnerId.class)
public class ShopPartner {

    @Id
    @Column(name = "shop_id", nullable = false)
    private String shopId;

    @Id
    @Column(name = "partner_id", nullable = false)
    private String partnerId;

    public ShopPartner() {}

    public ShopPartner(String shopId, String partnerId) {
        this.shopId = shopId;
        this.partnerId = partnerId;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }
}
