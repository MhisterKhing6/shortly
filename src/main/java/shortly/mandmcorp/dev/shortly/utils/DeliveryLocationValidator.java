package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates drop-off pins and station coordinates. All failures are 400 with a JSON message. */
@Component
public class DeliveryLocationValidator {

    private final boolean restrictToGhana;

    public DeliveryLocationValidator(@Value("${shortly.delivery-location.restrict-to-ghana:true}") boolean restrictToGhana) {
        this.restrictToGhana = restrictToGhana;
    }

    /** Validates a drop-off pin. Both null is allowed (no pin). */
    public void validateDropoff(Double latitude, Double longitude) {
        validate(latitude, longitude, "Delivery location");
    }

    /** Validates a pickup pin (where the rider collects from the sender). Both null is allowed. */
    public void validatePickup(Double latitude, Double longitude) {
        validate(latitude, longitude, "Pickup location");
    }

    /** Validates station coordinates. Both null is allowed. */
    public void validateStation(Double latitude, Double longitude) {
        validate(latitude, longitude, "Station location");
    }

    private void validate(Double latitude, Double longitude, String label) {
        if (latitude == null && longitude == null) return;
        if (latitude == null || longitude == null) {
            throw bad("Both latitude and longitude are required");
        }
        if (latitude.isNaN() || latitude < -90 || latitude > 90) {
            throw bad("Latitude must be between -90 and 90");
        }
        if (longitude.isNaN() || longitude < -180 || longitude > 180) {
            throw bad("Longitude must be between -180 and 180");
        }
        if (restrictToGhana && (latitude < 4.5 || latitude > 11.3 || longitude < -3.4 || longitude > 1.3)) {
            throw bad(label + " must be in Ghana");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
