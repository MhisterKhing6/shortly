package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.response.CounterCashupReportRow;
import shortly.mandmcorp.dev.shortly.dto.response.DriverPaymentViews;
import shortly.mandmcorp.dev.shortly.model.CounterCollection;
import shortly.mandmcorp.dev.shortly.model.DriverAssignment;
import shortly.mandmcorp.dev.shortly.service.counter.CounterService;
import shortly.mandmcorp.dev.shortly.service.driverpay.DriverPaymentService;

/** Admin reports for counter cash and driver payments. Managers see their own station. */
@RestController
@RequestMapping("/api-admin")
@AllArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Finance reports", description = "Counter cash-ups, POD payouts and driver payouts")
public class AdminFinanceController {

    private final CounterService counterService;
    private final DriverPaymentService driverPaymentService;
    private final shortly.mandmcorp.dev.shortly.service.driverpay.DriverPaymentIntegrityService integrityService;
    private final shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface riderService;

    @GetMapping("/counter-cashups")
    @Operation(summary = "Cash-ups by station and day", description = "Expected and counted cash, difference, MoMo, who closed it; closed=false rows are days with counter money not yet closed. Default: last 30 days.")
    public List<CounterCashupReportRow> cashups(@RequestParam(required = false) String officeId,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        return counterService.cashupReport(officeId, from, to);
    }

    @GetMapping("/pod-payouts")
    @Operation(summary = "POD values collected and still owed", description = "Item values collected at the counter not yet paid on to the sender or vendor (itemOwnerPaid=false).")
    public List<CounterCollection> podOwed(@RequestParam(required = false) String officeId) {
        return counterService.podOwed(officeId);
    }

    @GetMapping("/driver-payouts/summary")
    @Operation(summary = "Driver payouts per station and per driver", description = "Gross, deductions, bonuses, net, cash vs MoMo and unconfirmed payouts. Reversed payouts excluded.")
    public DriverPaymentViews.PayoutReport payoutSummary(@RequestParam(required = false) String officeId,
                                                         @RequestParam(required = false) String from,
                                                         @RequestParam(required = false) String to) {
        return driverPaymentService.payoutReport(officeId, from, to);
    }

    @GetMapping("/driver-trips/missing-parcels")
    @Operation(summary = "Trips with fewer parcels received than declared")
    public List<DriverPaymentViews.Trip> missingParcels(@RequestParam(required = false) String officeId,
                                                        @RequestParam(required = false) String from,
                                                        @RequestParam(required = false) String to) {
        return driverPaymentService.tripsWithMissingParcels(officeId, from, to);
    }

    @GetMapping("/driver-fee-lines/long-waiting")
    @Operation(summary = "Fees READY for more than N days without payment")
    public List<DriverAssignment> longWaiting(@RequestParam(required = false) String officeId,
                                              @RequestParam(defaultValue = "7") int days) {
        return driverPaymentService.longWaitingFees(officeId, days);
    }

    @PostMapping("/driver-payments/migrate")
    @Operation(summary = "One-off migration", description = "Creates driver records from existing fee lines' phone numbers, links the lines and gives each a state. Safe to re-run.")
    @TrackUserAction(action = "MIGRATE_DRIVER_PAYMENTS", description = "Ran the driver payments migration")
    public Map<String, Object> migrate() {
        return driverPaymentService.migrate();
    }

    @GetMapping("/assignments/duplicates")
    @Operation(summary = "Parcels on more than one active rider assignment")
    public List<Map<String, Object>> duplicateAssignments() {
        return riderService.findDuplicateAssignments();
    }

    @PostMapping("/assignments/duplicates/fix")
    @Operation(summary = "Remove duplicate assignments", description = "Keeps each parcel on its newest active assignment, takes it off the others, and links every parcel out with a rider to its assignment. Safe to re-run.")
    @TrackUserAction(action = "FIX_DUPLICATE_ASSIGNMENTS", description = "Removed duplicate rider assignments")
    public Map<String, Object> fixDuplicateAssignments() {
        return riderService.fixDuplicateAssignments();
    }

    @GetMapping("/driver-payments/integrity")
    @Operation(summary = "Driver money problems found by the nightly check", description = "PAID fees without a live payout, payout totals that don't match their fees, a fee on two payouts, advances whose repaid amount is wrong. Empty = all good.")
    public List<shortly.mandmcorp.dev.shortly.model.DriverPaymentIssue> integrity(@RequestParam(required = false) String officeId) {
        return integrityService.issues(officeId);
    }

    @PostMapping("/driver-payments/integrity/run")
    @Operation(summary = "Run the driver money check now", description = "ADMIN. Replaces the stored problems for your company and returns them.")
    public List<shortly.mandmcorp.dev.shortly.model.DriverPaymentIssue> runIntegrity() {
        return integrityService.runNow();
    }

    @PostMapping("/pod-payouts")
    @Operation(summary = "Record POD values paid on to senders", description = "Sets podPaidOut on each collection and itemOwnerPaid on the parcel. 409 if any is already paid out; 400 for MOMO without momoReference. Cash comes out of that day's counter cash.")
    @TrackUserAction(action = "POD_PAYOUT", description = "Paid POD money out to a sender")
    public List<CounterCollection> payOutPod(@org.springframework.web.bind.annotation.RequestBody shortly.mandmcorp.dev.shortly.dto.request.PodPayoutRequest request) {
        return counterService.payOutPod(request);
    }
}
