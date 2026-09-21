package in.dukkan.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.dukkan.domain.Shop;
import in.dukkan.domain.ShopEmployee;
import in.dukkan.domain.ShopStatus;
import in.dukkan.domain.ShopTransport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class ShopDtos {
    private ShopDtos() {}

    public record ShopView(
            String id,
            String name,
            String ownerUserId,
            String description,
            String address,
            double lat,
            double lng,
            BigDecimal rating,
            int reviewCount,
            boolean verified,
            String gstin,
            Integer yearStarted,
            ShopStatus status,
            boolean partnerDeliveryEnabled,
            boolean shopDeliveryEnabled,
            BigDecimal partnerDeliveryFee,
            BigDecimal shopDeliveryFee,
            BigDecimal minOrderAmount,
            String imageUrl,
            Set<String> categoryIds,
            @JsonProperty("isOpen") boolean isOpen,
            String openTime,
            String closeTime,
            boolean notificationsEnabled,
            boolean notifyOrderReceived,
            boolean notifyOrderStatus,
            boolean notifyStockConfirmation,
            List<ShopEmployee> employees,
            List<ShopTransport> transport) {

        public static ShopView from(Shop shop, List<ShopEmployee> employees, List<ShopTransport> transport) {
            return new ShopView(
                    shop.getId(),
                    shop.getName(),
                    shop.getOwnerUserId(),
                    shop.getDescription(),
                    shop.getAddress(),
                    shop.getLat(),
                    shop.getLng(),
                    shop.getRating(),
                    shop.getReviewCount(),
                    shop.isVerified(),
                    shop.getGstin(),
                    shop.getYearStarted(),
                    shop.getStatus(),
                    shop.isPartnerDeliveryEnabled(),
                    shop.isShopDeliveryEnabled(),
                    shop.getPartnerDeliveryFee(),
                    shop.getShopDeliveryFee(),
                    shop.getMinOrderAmount(),
                    shop.getImageUrl(),
                    shop.getCategoryIds(),
                    shop.isOpen(),
                    shop.getOpenTime(),
                    shop.getCloseTime(),
                    shop.isNotificationsEnabled(),
                    shop.isNotifyOrderReceived(),
                    shop.isNotifyOrderStatus(),
                    shop.isNotifyStockConfirmation(),
                    employees,
                    transport);
        }
    }

    public record ShopPatch(
            Boolean partnerDeliveryEnabled,
            Boolean shopDeliveryEnabled,
            BigDecimal partnerDeliveryFee,
            BigDecimal shopDeliveryFee,
            BigDecimal minOrderAmount,
            @JsonProperty("isOpen") Boolean isOpen,
            String openTime,
            String closeTime,
            ShopStatus status,
            Boolean notificationsEnabled,
            Boolean notifyOrderReceived,
            Boolean notifyOrderStatus,
            Boolean notifyStockConfirmation) {}

    public record EmployeeWrite(
            String shopId, String name, String role, String phone, Boolean available) {}

    public record TransportWrite(
            String shopId,
            String kind,
            String label,
            String registration,
            Integer capacityKg,
            Boolean available) {}
}
