package shortly.mandmcorp.dev.shortly.dto;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconciliationBatchRequest;
import tools.jackson.databind.json.JsonMapper;

/** Uses Jackson 3, which Spring Boot 4 uses for request bodies. */
class RequestBindingTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void reconciliationBodyAcceptsOneItemOrAnArray() {
        ReconciliationBatchRequest single = mapper.readValue(
                "{\"assignmentId\":\"a1\",\"reconciledAt\":\"1795349169203\",\"payedAmount\":\"30\",\"lines\":[{\"parcelId\":\"P1\",\"systemAmount\":40,\"collectedAmount\":30,\"variance\":-10}]}",
                ReconciliationBatchRequest.class);
        assertEquals(1, single.getItems().size());
        assertEquals(30.0, single.getItems().get(0).getPayedAmount());
        assertEquals(1795349169203L, single.getItems().get(0).getReconciledAt());
        assertEquals(-10.0, single.getItems().get(0).getLines().get(0).getVariance());

        ReconciliationBatchRequest many = mapper.readValue(
                "[{\"assignmentId\":\"a1\"},{\"assignmentId\":\"a2\"}]", ReconciliationBatchRequest.class);
        assertEquals(2, many.getItems().size());

        ReconciliationBatchRequest wrapped = mapper.readValue(
                "{\"items\":[{\"assignmentId\":\"a1\",\"payedAmount\":30,\"expectedAmount\":40,\"varianceAmount\":-10},{\"assignmentId\":\"a2\"}]}",
                ReconciliationBatchRequest.class);
        assertEquals(2, wrapped.getItems().size());
        assertEquals("a1", wrapped.getItems().get(0).getAssignmentId());
        assertEquals(-10.0, wrapped.getItems().get(0).getVarianceAmount());
    }

    @Test
    void userRegistrationAcceptsASingleOfficeId() {
        var one = mapper.readValue("{\"officeId\":\"off-1\"}", shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest.class);
        assertEquals(java.util.List.of("off-1"), one.getOfficeIds());
        var many = mapper.readValue("{\"officeIds\":[\"a\",\"b\"],\"officeId\":\"c\"}", shortly.mandmcorp.dev.shortly.dto.request.UserRegistrationRequest.class);
        assertEquals(java.util.List.of("a", "b"), many.getOfficeIds());
    }

    @Test
    void parcelUpdateDistinguishesAbsentFromExplicitNullLocation() {
        ParcelUpdateRequest absent = mapper.readValue("{\"receiverName\":\"Ama\"}", ParcelUpdateRequest.class);
        assertFalse(absent.isReceiverLocationProvided());

        ParcelUpdateRequest cleared = mapper.readValue(
                "{\"receiverLatitude\":null,\"receiverLongitude\":null,\"receiverLocationName\":null,\"receiverPlaceId\":null,\"receiverLocationSource\":null}",
                ParcelUpdateRequest.class);
        assertTrue(cleared.isReceiverLocationProvided());
        assertNull(cleared.getReceiverLatitude());

        ParcelUpdateRequest set = mapper.readValue(
                "{\"receiverLatitude\":5.6223,\"receiverLongitude\":-0.1735,\"receiverLocationSource\":\"MAP_PIN\"}",
                ParcelUpdateRequest.class);
        assertTrue(set.isReceiverLocationProvided());
        assertEquals(5.6223, set.getReceiverLatitude());
        assertFalse(mapper.writeValueAsString(set).contains("receiverLocationProvided"));
    }

    @Test
    void pickupPinFollowsTheSameAbsentVersusNullRule() {
        ParcelUpdateRequest absent = mapper.readValue("{\"receiverLatitude\":5.6,\"receiverLongitude\":-0.17}", ParcelUpdateRequest.class);
        assertTrue(absent.isReceiverLocationProvided());
        assertFalse(absent.isPickupLocationProvided());
        ParcelUpdateRequest cleared = mapper.readValue("{\"pickupLatitude\":null,\"pickupLongitude\":null}", ParcelUpdateRequest.class);
        assertTrue(cleared.isPickupLocationProvided());
        assertFalse(cleared.isReceiverLocationProvided());
    }
}
