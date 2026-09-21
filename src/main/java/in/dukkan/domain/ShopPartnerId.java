package in.dukkan.domain;

import java.io.Serializable;
import java.util.Objects;

public class ShopPartnerId implements Serializable {

    private String shopId;
    private String partnerId;

    public ShopPartnerId() {}

    public ShopPartnerId(String shopId, String partnerId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ShopPartnerId other)) {
            return false;
        }
        return Objects.equals(shopId, other.shopId) && Objects.equals(partnerId, other.partnerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(shopId, partnerId);
    }
}
