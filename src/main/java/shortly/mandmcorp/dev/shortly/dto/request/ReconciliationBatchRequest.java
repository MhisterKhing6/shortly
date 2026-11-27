package shortly.mandmcorp.dev.shortly.dto.request;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.annotation.JsonCreator;

import lombok.Getter;
import shortly.mandmcorp.dev.shortly.model.ReconciliationLine;

/**
 * Body of POST /api-frontdesk/reconcilation-parcels. Accepts all three shapes the frontend has used:
 * {"items": [...]} (current), a single item, or a bare array of items.
 *
 * Bound by hand from the untyped JSON (maps and lists) so it works the same under Jackson 2 and 3.
 * Numbers may arrive as strings ("30", "1795349169203").
 */
@Getter
public class ReconciliationBatchRequest {
    private final List<ReconcilationRiderRequest> items = new ArrayList<>();

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public ReconciliationBatchRequest(Object body) {
        if (body instanceof List<?> list) {
            list.forEach(o -> items.add(item(o)));
        } else if (body instanceof Map<?, ?> map && map.get("items") instanceof List<?> list) {
            list.forEach(o -> items.add(item(o)));
        } else if (body instanceof Map<?, ?> map) {
            items.add(item(map));
        } else {
            throw bad("Expected {\"items\": [...]}, an item, or an array of items");
        }
    }

    private static ReconcilationRiderRequest item(Object o) {
        if (!(o instanceof Map<?, ?> m)) throw bad("Each reconciliation item must be an object");
        ReconcilationRiderRequest r = new ReconcilationRiderRequest();
        r.setAssignmentId(str(m.get("assignmentId")));
        Double reconciledAt = num(m.get("reconciledAt"), "reconciledAt");
        r.setReconciledAt(reconciledAt == null ? null : reconciledAt.longValue());
        r.setPayedAmount(num(m.get("payedAmount"), "payedAmount"));
        r.setExpectedAmount(num(m.get("expectedAmount"), "expectedAmount"));
        r.setVarianceAmount(num(m.get("varianceAmount"), "varianceAmount"));
        if (m.get("lines") instanceof List<?> lines) {
            List<ReconciliationLine> out = new ArrayList<>();
            for (Object lo : lines) {
                if (!(lo instanceof Map<?, ?> l)) throw bad("Each line must be an object");
                out.add(ReconciliationLine.builder()
                        .parcelId(str(l.get("parcelId")))
                        .systemAmount(num(l.get("systemAmount"), "systemAmount"))
                        .collectedAmount(num(l.get("collectedAmount"), "collectedAmount"))
                        .variance(num(l.get("variance"), "variance"))
                        .varianceReason(str(l.get("varianceReason")))
                        .varianceNote(str(l.get("varianceNote")))
                        .build());
            }
            r.setLines(out);
        }
        return r;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString();
    }

    private static Double num(Object v, String field) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            throw bad(field + " must be a number");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
