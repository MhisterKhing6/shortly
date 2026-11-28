package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.model.DeliveryPricingTable;
import shortly.mandmcorp.dev.shortly.service.pricing.DeliveryPricingService;

/** Distance-based delivery pricing (Part 5c). */
@RestController
@AllArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@Tag(name = "Delivery pricing", description = "Station price tables, quotes and the pricing review")
public class DeliveryPricingController {

    private final DeliveryPricingService service;

    @Data
    public static class QuoteRequest {
        private Double latitude;
        private Double longitude;
        private Double weightKg;
        private boolean fragile;
    }

    @GetMapping("/api-frontdesk/office/delivery-pricing")
    @Operation(summary = "Your station's delivery price table", description = "404 if none is set. Includes version, updatedAt and updatedByName.")
    public DeliveryPricingTable ownTable() {
        return service.ownTable();
    }

    @PutMapping("/api-frontdesk/office/delivery-pricing")
    @Operation(summary = "Save your station's delivery price table", description = "MANAGER. Saved as a new version; old versions are kept.")
    @TrackUserAction(action = "SAVE_DELIVERY_PRICING", description = "Manager saved the delivery price table")
    public DeliveryPricingTable saveOwnTable(@RequestBody DeliveryPricingTable table) {
        return service.saveOwnTable(table);
    }

    @PostMapping("/api-frontdesk/delivery-pricing/quote")
    @Operation(summary = "Delivery price for a pin", description = "Road distance from your station (Google Routes, cached); straight line × 1.35 with distanceSource ESTIMATE if that fails. 404 if the station has no table.")
    public DeliveryPricingService.Quote quote(@RequestBody QuoteRequest request) {
        return service.staffQuote(request.getLatitude(), request.getLongitude(), request.getWeightKg(), request.isFragile());
    }

    @GetMapping("/api-admin/offices/{officeId}/delivery-pricing")
    public DeliveryPricingTable officeTable(@PathVariable String officeId) {
        return service.officeTable(officeId);
    }

    @PutMapping("/api-admin/offices/{officeId}/delivery-pricing")
    @Operation(summary = "Save a station's price table (admin)", description = "Saved as a new version, with an optional note describing the changes.")
    @TrackUserAction(action = "ADMIN_SAVE_DELIVERY_PRICING", description = "Admin saved a station's delivery price table")
    public DeliveryPricingTable saveOfficeTable(@PathVariable String officeId, @RequestBody DeliveryPricingTable table) {
        return service.saveOfficeTable(officeId, table);
    }

    @GetMapping("/api-admin/delivery-pricing/review")
    @Operation(summary = "Pricing review per band and zone", description = "Prices and overrides on the current table version; trip cost from saved route plans for every delivery in the period; quotes shown vs booked; staff overrides. Dates yyyy-MM-dd (default last 8 weeks).")
    public DeliveryPricingService.Review review(@RequestParam String officeId,
                                                @RequestParam(required = false) String from,
                                                @RequestParam(required = false) String to) {
        return service.review(officeId, from, to);
    }
}
