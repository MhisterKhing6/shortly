package shortly.mandmcorp.dev.shortly.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.ReconciliationLine;

class ReconciliationValidatorTest {

    private static DeliveryAssignments assignment() {
        DeliveryAssignments a = new DeliveryAssignments();
        a.setAssignmentId("asg-1");
        a.setParcels(List.of(ParcelInfo.builder().parcelId("PF1").build(), ParcelInfo.builder().parcelId("PF2").build()));
        return a;
    }

    private static ReconcilationRiderRequest request(Double varianceAmount, ReconciliationLine... lines) {
        ReconcilationRiderRequest r = new ReconcilationRiderRequest();
        r.setAssignmentId("asg-1");
        r.setVarianceAmount(varianceAmount);
        r.setLines(List.of(lines));
        return r;
    }

    private static ReconciliationLine line(String id, double system, double collected, Double variance, String reason, String note) {
        return ReconciliationLine.builder().parcelId(id).systemAmount(system).collectedAmount(collected)
                .variance(variance).varianceReason(reason).varianceNote(note).build();
    }

    @Test
    void acceptsMatchingLinesAndLegacyRequests() {
        assertDoesNotThrow(() -> ReconciliationValidator.validate(request(-10.0,
                line("PF1", 40, 30, -10.0, "Other", "Customer will bring GHC 10 tomorrow"),
                line("PF2", 20, 20, 0.0, null, null)), assignment()));
        assertDoesNotThrow(() -> ReconciliationValidator.validate(new ReconcilationRiderRequest(), assignment()));
    }

    @Test
    void rejectsBadLines() {
        assertBad(request(null, line("PFX", 40, 40, 0.0, null, null)));          // not in assignment
        assertBad(request(null, line("PF1", 40, 30, -5.0, "Discount approved", null))); // wrong variance
        assertBad(request(null, line("PF1", 40, 30, -10.0, null, null)));       // no reason
        assertBad(request(null, line("PF1", 40, 30, -10.0, "Other", " ")));     // Other without note
        assertBad(request(-3.0, line("PF1", 40, 30, -10.0, "Customer paid less", null))); // sum mismatch
    }

    private static void assertBad(ReconcilationRiderRequest r) {
        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> ReconciliationValidator.validate(r, assignment()));
        assertEquals(400, e.getStatusCode().value());
    }
}
