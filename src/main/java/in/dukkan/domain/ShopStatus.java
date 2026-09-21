package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShopStatus {
    PENDING,
    ACTIVE,
    SUSPENDED;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ShopStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}
