package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.CashupAdjustmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.CounterCashupRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CounterCashMovementsResponse;
import shortly.mandmcorp.dev.shortly.model.CounterCashup;
import shortly.mandmcorp.dev.shortly.model.CounterCollection;
import shortly.mandmcorp.dev.shortly.service.counter.CounterService;

/** Counter pickup money and the daily cash-up (Part L). Pickups themselves go through POST /parcel/picked-up. */
@RestController
@RequestMapping("/api-frontdesk")
@AllArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Counter cash-up", description = "Counter pickup payments and the daily cash-up")
public class CounterController {

    private final CounterService counterService;

    @GetMapping("/counter-collections")
    @Operation(summary = "Counter pickups for a day", description = "Station-local day (yyyy-MM-dd, default today), sorted by pickedUpAt. Front desk and managers: own office; admins pass officeId.")
    public List<CounterCollection> collections(@RequestParam(required = false) String date,
                                               @RequestParam(required = false) String officeId) {
        return counterService.collections(date, officeId);
    }

    @GetMapping("/counter-cash-movements")
    @Operation(summary = "Everything through the drawer for a day", description = "Counter collections, driver cash payouts and cash advances, with totals and the expected cash.")
    public CounterCashMovementsResponse cashMovements(@RequestParam(required = false) String date,
                                                      @RequestParam(required = false) String officeId) {
        return counterService.cashMovements(date, officeId);
    }

    @PostMapping("/counter-cashup")
    @Operation(summary = "Close a counter day", description = "MANAGER, own office. Expected figures are recomputed on the server. 409 if the day is already closed; a note is required when counted cash differs.")
    @TrackUserAction(action = "CLOSE_COUNTER_DAY", description = "Manager closed the counter cash-up")
    public Map<String, String> closeDay(@RequestBody CounterCashupRequest request) {
        return counterService.closeDay(request);
    }

    @GetMapping("/counter-cashup")
    @Operation(summary = "A closed counter day", description = "404 if the day isn't closed yet.")
    public CounterCashup getCashup(@RequestParam(required = false) String date,
                                   @RequestParam(required = false) String officeId) {
        return counterService.getCashup(date, officeId);
    }

    @PostMapping("/counter-cashup/adjustments")
    @Operation(summary = "Correct a closed day", description = "MANAGER. Closed days are never edited; this appends a correction.")
    @TrackUserAction(action = "ADJUST_COUNTER_DAY", description = "Manager added a correction to a closed cash-up")
    public CounterCashup addAdjustment(@RequestBody CashupAdjustmentRequest request) {
        return counterService.addAdjustment(request.getDate(), request.getAmount(), request.getReason());
    }
}
