package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderType {
    PRODUCT_BUSINESS,
    SERVICE_BUSINESS,
    INDIVIDUAL;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static ProviderType fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}
