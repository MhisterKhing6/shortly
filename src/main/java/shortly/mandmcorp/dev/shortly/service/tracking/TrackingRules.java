package shortly.mandmcorp.dev.shortly.service.tracking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Thresholds for delivery and off-task checks. Defaults match the frontend's trackUtils.ts so
 * server reports agree with the Rider Map. Override with shortly.tracking.* properties.
 */
@Component
@Getter
@NoArgsConstructor
public class TrackingRules {
    @Value("${shortly.tracking.visit-radius-m:150}")
    private double visitRadiusM = 150;
    @Value("${shortly.tracking.visit-grace-min:30}")
    private long visitGraceMin = 30;
    @Value("${shortly.tracking.marked-away-m:300}")
    private double markedAwayM = 300;
    @Value("${shortly.tracking.stop-speed-kmh:3}")
    private double stopSpeedKmh = 3;
    @Value("${shortly.tracking.stop-min-minutes:5}")
    private long stopMinMinutes = 5;
    @Value("${shortly.tracking.max-gap-min:15}")
    private long maxGapMin = 15;
    @Value("${shortly.tracking.detour-corridor-m:1500}")
    private double detourCorridorM = 1500;
    @Value("${shortly.tracking.detour-min-minutes:3}")
    private long detourMinMinutes = 3;
    @Value("${shortly.tracking.off-task-stop-radius-m:200}")
    private double offTaskStopRadiusM = 200;
    @Value("${shortly.tracking.plan-corridor-google-m:500}")
    private double planCorridorGoogleM = 500;
    @Value("${shortly.tracking.plan-corridor-estimate-m:1500}")
    private double planCorridorEstimateM = 1500;

    public static TrackingRules defaults() {
        return new TrackingRules();
    }
}
