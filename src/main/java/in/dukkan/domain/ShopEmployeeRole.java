package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShopEmployeeRole {
    RIDER,
    PACKER,
    DISPATCHER,
    MANAGER;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ShopEmployeeRole fromJson(String value) {
        if (value == null || value.isBlank()) {
            return RIDER;
        }
        return valueOf(value.trim().toUpperCase());
    }
}
