package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ShopTransportKind {
    BIKE,
    SCOOTER,
    CYCLE,
    TEMPO,
    VAN,
    TRUCK;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static ShopTransportKind fromJson(String value) {
        if (value == null || value.isBlank()) {
            return BIKE;
        }
        return valueOf(value.trim().toUpperCase().replace(' ', '_'));
    }
}
