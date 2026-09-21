package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DeliveryMode {
    PARTNER,
    SHOP;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static DeliveryMode fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}
