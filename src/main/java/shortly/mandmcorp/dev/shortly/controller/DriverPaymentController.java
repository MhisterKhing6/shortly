package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.AdjustmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.ConfirmRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.DriverRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.PayoutRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.ReasonRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DriverPaymentRequests.TripRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews;
import shortly.mandmcorp.dev.shortly.model.Driver;
import shortly.mandmcorp.dev.shortly.model.DriverAdjustment;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.model.DriverPayout;
import shortly.mandmcorp.dev.shortly.model.DriverTrip;
import shortly.mandmcorp.dev.shortly.service.driverpay.DriverPaymentService;

/** Driver Payments (Part M). Dates accept epoch ms or yyyy-MM-dd. */
@RestController
@RequestMapping("/api-frontdesk")
@AllArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Driver payments", description = "Drivers, trips, fee lines, payouts and adjustments")
public class DriverPaymentController {

    private final DriverPaymentService service;

    // ---- drivers
    @GetMapping("/drivers")
    @Operation(summary = "Search drivers", description = "By name, phone, vehicle or company. Paged.")
    public Page<Driver> drivers(@RequestParam(required = false) String search,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size) {
        return service.searchDrivers(search, page, size);
    }

    @PostMapping("/drivers")
    @TrackUserAction(action = "CREATE_DRIVER", description = "Created a driver record")
    public Driver createDriver(@RequestBody DriverRequest request) {
        return service.createDriver(request);
    }

    @PutMapping("/drivers/{id}")
    @TrackUserAction(action = "UPDATE_DRIVER", description = "Updated a driver record")
    public Driver updateDriver(@PathVariable String id, @RequestBody DriverRequest request) {
        return service.updateDriver(id, request);
    }

    // ---- trips
    @PostMapping("/driver-trips")
    @Operation(summary = "Open a trip when a driver arrives", description = "Returns the trip; send its id as tripId on each parcel.")
    @TrackUserAction(action = "OPEN_DRIVER_TRIP", description = "Opened a driver trip")
    public Map<String, Object> createTrip(@RequestBody TripRequest request) {
        DriverTrip trip = service.createTrip(request);
        return Map.of("tripId", trip.getId(), "trip", trip);
    }

    @GetMapping("/driver-trips")
    @Operation(summary = "Trips with counts and fee totals", description = "flagged = received count differs from declared.")
    public List<DriverPaymentViews.Trip> trips(@RequestParam(required = false) String driverId,
                                               @RequestParam(required = false) String from,
                                               @RequestParam(required = false) String to) {
        return service.listTrips(driverId, from, to);
    }

    // ---- balances, statements, summary
    @GetMapping("/driver-balances")
    @Operation(summary = "One row per driver with balances", description = "state = READY (default), ALL or ON_HOLD. Sorted by readyAmount descending.")
    public Page<DriverPaymentViews.BalanceRow> balances(@RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "READY") String state,
                                                        @RequestParam(required = false) String officeId,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.balances(search, state, officeId, page, size);
    }

    @GetMapping("/drivers/{id}/statement")
    @Operation(summary = "Driver statement", description = "Balances, trips (newest first) with fee lines, payouts and open adjustments. Trips with unpaid fees are always included.")
    public DriverPaymentViews.Statement statement(@PathVariable String id,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to) {
        return service.statement(id, from, to);
    }

    @GetMapping("/driver-payments/summary")
    @Operation(summary = "Ready, waiting, paid this week and on hold")
    public DriverPaymentViews.Summary summary(@RequestParam(required = false) String officeId) {
        return service.summary(officeId);
    }

    // ---- fee lines
    @PutMapping("/driver-fee-lines/{id}/hold")
    @TrackUserAction(action = "HOLD_DRIVER_FEE", description = "Put a driver fee on hold")
    public DriverAssignment hold(@PathVariable String id, @RequestBody ReasonRequest request) {
        return service.hold(id, request.getReason());
    }

    @PutMapping("/driver-fee-lines/{id}/release")
    @TrackUserAction(action = "RELEASE_DRIVER_FEE", description = "Released a held driver fee")
    public DriverAssignment release(@PathVariable String id) {
        return service.release(id);
    }

    // ---- adjustments
    @PostMapping("/drivers/{id}/adjustments")
    @Operation(summary = "Record an advance, deduction or bonus", description = "MANAGER/ADMIN. A cash ADVANCE comes out of today's counter drawer.")
    @TrackUserAction(action = "DRIVER_ADJUSTMENT", description = "Recorded a driver adjustment")
    public DriverAdjustment addAdjustment(@PathVariable String id, @RequestBody AdjustmentRequest request) {
        return service.addAdjustment(id, request);
    }

    @GetMapping("/drivers/{id}/adjustments")
    public List<DriverAdjustment> adjustments(@PathVariable String id, @RequestParam(required = false) Boolean open) {
        return service.adjustments(id, open);
    }

    // ---- payouts
    @PostMapping("/driver-payouts")
    @Operation(summary = "Pay a driver", description = "MANAGER/ADMIN. Every line must be READY and belong to the driver. net = gross − deductions − advances repaid + bonuses, never negative.")
    @TrackUserAction(action = "DRIVER_PAYOUT", description = "Paid a driver")
    public DriverPayout pay(@RequestBody PayoutRequest request) {
        return service.createPayout(request);
    }

    @GetMapping("/driver-payouts")
    public List<DriverPayout> payouts(@RequestParam(required = false) String driverId,
                                      @RequestParam(required = false) String from,
                                      @RequestParam(required = false) String to,
                                      @RequestParam(required = false) Boolean unconfirmed,
                                      @RequestParam(required = false) String officeId) {
        return service.listPayouts(driverId, from, to, unconfirmed, officeId);
    }

    @GetMapping("/driver-payouts/{id}")
    @Operation(summary = "Payout receipt")
    public DriverPayout payout(@PathVariable String id) {
        return service.getPayout(id);
    }

    @PostMapping("/driver-payouts/{id}/send-code")
    @Operation(summary = "Text the driver a 4-digit confirmation code")
    public Map<String, String> sendCode(@PathVariable String id) {
        return service.sendCode(id);
    }

    @PutMapping("/driver-payouts/{id}/confirm")
    @TrackUserAction(action = "CONFIRM_DRIVER_PAYOUT", description = "Driver confirmed a payout")
    public DriverPayout confirm(@PathVariable String id, @RequestBody ConfirmRequest request) {
        return service.confirm(id, request.getCode());
    }

    @PostMapping("/driver-payouts/{id}/reverse")
    @Operation(summary = "Reverse a payout", description = "MANAGER/ADMIN. Lines go back to READY; the payout stays in the history as reversed.")
    @TrackUserAction(action = "REVERSE_DRIVER_PAYOUT", description = "Reversed a driver payout")
    public DriverPayout reverse(@PathVariable String id, @RequestBody ReasonRequest request) {
        return service.reverse(id, request.getReason());
    }
}
