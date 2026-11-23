package shortly.mandmcorp.dev.shortly.utils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.ReconciliationLine;

/**
 * Rules for per-parcel reconciliation lines:
 * every line belongs to the assignment, variance = collected − system (2 dp),
 * a non-zero variance needs a reason, and "Other" needs a note.
 */
public final class ReconciliationValidator {

    public static final String REASON_OTHER = "Other";
    private static final double TOLERANCE = 0.005;

    private ReconciliationValidator() {}

    public static void validate(ReconcilationRiderRequest request, DeliveryAssignments assignment) {
        List<ReconciliationLine> lines = request.getLines();
        if (lines == null) return; // older clients send no lines

        Set<String> assignmentParcelIds = new HashSet<>();
        if (assignment.getParcels() != null) {
            for (ParcelInfo p : assignment.getParcels()) assignmentParcelIds.add(p.getParcelId());
        }

        double varianceSum = 0;
        for (ReconciliationLine line : lines) {
            String id = line.getParcelId();
            if (id == null || !assignmentParcelIds.contains(id)) {
                throw bad("Parcel " + id + " does not belong to assignment " + assignment.getAssignmentId());
            }
            if (line.getSystemAmount() == null || line.getCollectedAmount() == null) {
                throw bad("Parcel " + id + ": systemAmount and collectedAmount are required");
            }
            double expected = round2(line.getCollectedAmount() - line.getSystemAmount());
            double variance = line.getVariance() != null ? line.getVariance() : expected;
            if (Math.abs(round2(variance) - expected) > TOLERANCE) {
                throw bad("Parcel " + id + ": variance must equal collectedAmount - systemAmount");
            }
            line.setVariance(expected);
            if (Math.abs(expected) > TOLERANCE) {
                if (isBlank(line.getVarianceReason())) {
                    throw bad("Parcel " + id + ": a variance reason is required");
                }
                if (REASON_OTHER.equalsIgnoreCase(line.getVarianceReason().trim()) && isBlank(line.getVarianceNote())) {
                    throw bad("Parcel " + id + ": a note is required when the reason is Other");
                }
            } else {
                line.setVarianceReason(null);
                line.setVarianceNote(null);
            }
            varianceSum += expected;
        }

        if (request.getVarianceAmount() != null && Math.abs(round2(request.getVarianceAmount()) - round2(varianceSum)) > TOLERANCE) {
            throw bad("varianceAmount must equal the sum of line variances");
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
