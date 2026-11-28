package shortly.mandmcorp.dev.shortly.service.tracking;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckResponse;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryCheckSummaryRow;
import shortly.mandmcorp.dev.shortly.dto.response.RiderTrackResponse;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.ParcelInfo;
import shortly.mandmcorp.dev.shortly.model.RoutePlan;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.DeliveryInput;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.PlanLine;
import shortly.mandmcorp.dev.shortly.service.tracking.DeliveryCheckAnalyzer.Point;
import shortly.mandmcorp.dev.shortly.utils.CurrentUser;
import shortly.mandmcorp.dev.shortly.utils.GeoUtils;

/** Server-side delivery and off-task checks (Parts D2, D3, E). */
@Service
@Slf4j
public class DeliveryCheckService {

    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration MAX_RANGE = Duration.ofDays(2);
    /** Assignments are grouped per rider per day; look back this far for ones still open in the period. */
    private static final long ASSIGNMENT_LOOKBACK_MS = Duration.ofDays(7).toMillis();

    private final RiderTrackingService riderTrackingService;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;
    private final MongoTemplate mongoTemplate;
    private final TrackingRules rules;

    public DeliveryCheckService(RiderTrackingService riderTrackingService, UserRepository userRepository,
                                OfficeRepository officeRepository, MongoTemplate mongoTemplate, TrackingRules rules) {
        this.riderTrackingService = riderTrackingService;
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
        this.mongoTemplate = mongoTemplate;
        this.rules = rules;
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public DeliveryCheckResponse check(String phoneNumber, String beginTime, String endTime) {
        long begin = parseUtc(beginTime, "beginTime");
        long end = parseUtc(endTime, "endTime");
        if (end <= begin) throw bad("endTime must be after beginTime");
        if (end - begin > MAX_RANGE.toMillis()) throw bad("The period can be at most 2 days");

        User rider = riderTrackingService.requireRider(phoneNumber);
        return checkRider(rider, beginTime, endTime, begin, end);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public List<DeliveryCheckSummaryRow> dailySummary(String officeId, String date) {
        LocalDate day;
        try {
            day = (date == null || date.isBlank()) ? LocalDate.now(ZoneOffset.UTC) : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw bad("date must be yyyy-MM-dd");
        }
        LocalDateTime startLdt = day.atStartOfDay();
        LocalDateTime endLdt = day.atTime(23, 59, 59);
        long begin = startLdt.toInstant(ZoneOffset.UTC).toEpochMilli();
        long end = endLdt.toInstant(ZoneOffset.UTC).toEpochMilli();

        String scopedOffice = CurrentUser.scopedOfficeId(officeId);
        List<User> riders = CurrentUser.ridersInScope(userRepository, scopedOffice);

        List<DeliveryCheckSummaryRow> rows = new ArrayList<>();
        for (User rider : riders) {
            if (rider.getDeviceImei() == null || rider.getDeviceImei().isBlank()) continue;
            try {
                DeliveryCheckResponse r = checkRider(rider, TIME_FORMAT.format(startLdt), TIME_FORMAT.format(endLdt), begin, end);
                DeliveryCheckResponse.DeliveryCheckSummary s = r.getSummary();
                DeliveryCheckResponse.OffTask o = r.getOffTask();
                rows.add(DeliveryCheckSummaryRow.builder()
                        .riderId(rider.getUserId()).riderName(rider.getName())
                        .deliveries(s.getDeliveries()).reached(s.getReached()).notReached(s.getNotReached())
                        .noPin(s.getNoPin()).markedAway(s.getMarkedAway())
                        .distanceKm(s.getDistanceKm()).movingMinutes(s.getMovingMinutes()).stoppedMinutes(s.getStoppedMinutes())
                        .judged(o.isJudged()).directKm(o.getDirectKm()).detours(o.getDetours().size())
                        .offRouteMinutes(o.getOffRouteMinutes()).offRouteKm(o.getOffRouteKm())
                        .offTaskStops(o.getOffTaskStops().size()).offTaskStopMinutes(o.getOffTaskStopMinutes())
                        .build());
            } catch (Exception e) {
                log.warn("Delivery check failed for rider {}: {}", rider.getUserId(), e.getMessage());
                rows.add(DeliveryCheckSummaryRow.builder()
                        .riderId(rider.getUserId()).riderName(rider.getName())
                        .error(e instanceof ResponseStatusException rse ? rse.getReason() : "Check failed")
                        .build());
            }
        }
        return rows;
    }

    private DeliveryCheckResponse checkRider(User rider, String beginTime, String endTime, long begin, long end) {
        RiderTrackResponse track = riderTrackingService.fetchTrack(rider, beginTime, endTime);
        List<Point> points = new ArrayList<>();
        if (track.getTrackPoints() != null) {
            for (RiderTrackResponse.TrackPoint tp : track.getTrackPoints()) {
                if (tp.getLat() == null || tp.getLng() == null || tp.getGpsTime() == null) continue;
                try {
                    long t = parseUtc(tp.getGpsTime(), "gpsTime");
                    if (t >= begin && t <= end) points.add(new Point(tp.getLat(), tp.getLng(), t));
                } catch (ResponseStatusException ignored) {
                    // skip unparseable fixes
                }
            }
        }

        List<DeliveryAssignments> assignments = mongoTemplate.find(new Query(new Criteria().andOperator(
                Criteria.where("riderInfo.riderId").is(rider.getUserId()),
                Criteria.where("assignedAt").gte(begin - ASSIGNMENT_LOOKBACK_MS).lte(end))), DeliveryAssignments.class);

        List<DeliveryInput> inputs = new ArrayList<>();
        List<PlanLine> plans = new ArrayList<>();
        Set<String> seenPlans = new HashSet<>();
        for (DeliveryAssignments a : assignments) {
            if (a.getParcels() == null) continue;
            boolean anyInPeriod = false;
            for (ParcelInfo p : a.getParcels()) {
                Long completedAt = completedAt(p, a);
                // Include parcels whose window overlaps the period.
                if (a.getAssignedAt() > end) continue;
                if (completedAt != null && completedAt + rules.getVisitGraceMin() * 60_000L < begin) continue;
                anyInPeriod = true;
                inputs.add(DeliveryInput.builder()
                        .assignmentId(a.getAssignmentId())
                        .parcelId(p.getParcelId())
                        .receiverName(p.getReceiverName())
                        .receiverLocationName(p.getReceiverLocationName())
                        .lat(p.getReceiverLatitude())
                        .lng(p.getReceiverLongitude())
                        .status(p.isDelivered() ? "DELIVERED" : p.isReturned() ? "RETURNED"
                                : (a.getStatus() != null ? a.getStatus().name() : null))
                        .assignedAt(a.getAssignedAt())
                        .completedAt(completedAt)
                        .markedDistanceFromDropoffM(p.getMarkedDistanceFromDropoffM())
                        .build());
            }
            RoutePlan plan = a.getRoutePlan();
            if (anyInPeriod && plan != null && plan.getEncodedPath() != null
                    && seenPlans.add(plan.getPlannedAt() + "|" + plan.getStopOrder())) {
                double corridor = "GOOGLE".equals(plan.getMethod()) ? rules.getPlanCorridorGoogleM() : rules.getPlanCorridorEstimateM();
                plans.add(new PlanLine(GeoUtils.decodePolyline(plan.getEncodedPath()), corridor));
            }
        }

        Double stationLat = null, stationLng = null;
        String officeId = CurrentUser.officeId(rider);
        if (officeId != null) {
            Office office = officeRepository.findById(officeId).orElse(null);
            if (office != null) {
                stationLat = office.getLatitude();
                stationLng = office.getLongitude();
            }
        }

        DeliveryCheckResponse response = new DeliveryCheckAnalyzer(rules).analyze(points, inputs, stationLat, stationLng, plans);
        response.setRiderId(rider.getUserId());
        response.setRiderName(rider.getName());
        return response;
    }

    /** Per-parcel completion time; falls back to the assignment's for parcels marked before it was recorded. */
    private static Long completedAt(ParcelInfo p, DeliveryAssignments a) {
        if (!p.isDelivered() && !p.isReturned()) return null;
        if (p.getCompletedAt() != null) return p.getCompletedAt();
        if (a.getCompletedAt() > 0) return a.getCompletedAt();
        return a.getUpdatedAt();
    }

    static long parseUtc(String value, String field) {
        try {
            return LocalDateTime.parse(value.trim(), TIME_FORMAT).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException | NullPointerException e) {
            throw bad(field + " must be UTC yyyy-MM-dd HH:mm:ss");
        }
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
