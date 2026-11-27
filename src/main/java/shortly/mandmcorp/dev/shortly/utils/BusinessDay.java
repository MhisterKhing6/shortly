package shortly.mandmcorp.dev.shortly.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Station-local calendar days (Ghana, UTC+0) for counter cash-ups and driver payouts. */
public final class BusinessDay {

    public static final ZoneId ZONE = ZoneId.of("Africa/Accra");

    private BusinessDay() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate of(long epochMs) {
        return Instant.ofEpochMilli(epochMs).atZone(ZONE).toLocalDate();
    }

    /** Parses yyyy-MM-dd (400 on bad input); null/blank means today. */
    public static LocalDate parse(String date) {
        if (date == null || date.isBlank()) return today();
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date must be yyyy-MM-dd");
        }
    }

    public static long startMs(LocalDate day) {
        return day.atStartOfDay(ZONE).toInstant().toEpochMilli();
    }

    /** Exclusive end of the day. */
    public static long endMs(LocalDate day) {
        return day.plusDays(1).atStartOfDay(ZONE).toInstant().toEpochMilli();
    }
}
