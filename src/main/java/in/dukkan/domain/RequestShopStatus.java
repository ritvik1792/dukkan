package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RequestShopStatus {
    QUEUED,
    NOTIFIED,
    VIEWED,
    ACCEPTED,
    DECLINED,
    NO_REPLY,
    SKIPPED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static RequestShopStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }

    public boolean hasResponded() {
        return this == ACCEPTED || this == DECLINED;
    }
}
