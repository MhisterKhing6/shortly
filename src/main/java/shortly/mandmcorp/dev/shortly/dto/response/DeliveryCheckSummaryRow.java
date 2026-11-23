package shortly.mandmcorp.dev.shortly.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One rider's row in the daily delivery-check summary (Part D3). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryCheckSummaryRow {
    private String riderId;
    private String riderName;
    private Integer deliveries;
    private Integer reached;
    private Integer notReached;
    private Integer noPin;
    private Integer markedAway;
    private Double distanceKm;
    private Long movingMinutes;
    private Long stoppedMinutes;
    private Double directKm;
    private Integer detours;
    private Long offRouteMinutes;
    private Double offRouteKm;
    private Integer offTaskStops;
    private Long offTaskStopMinutes;
    private Boolean judged;
    /** Set when this rider couldn't be checked (e.g. tracker unavailable). */
    private String error;
}
