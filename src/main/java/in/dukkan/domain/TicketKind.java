package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TicketKind {
    COMPLAINT,
    SUPPORT;

    @JsonValue
    public String toJson() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static TicketKind fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }
}
