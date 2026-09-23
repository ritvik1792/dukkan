package in.dukkan.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BookingStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    REJECTED,
    CANCELLED;

    @JsonValue
    public String toJson() {
        return name();
    }

    @JsonCreator
    public static BookingStatus fromJson(String value) {
        return value == null ? null : valueOf(value.trim().toUpperCase());
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    public boolean canTransitionTo(BookingStatus next) {
        if (next == null || this == next) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == REJECTED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, REJECTED, CANCELLED -> false;
        };
    }
}
