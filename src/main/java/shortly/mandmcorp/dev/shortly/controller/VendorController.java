package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.VendorParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.VendorDashboardResponse;
import shortly.mandmcorp.dev.shortly.dto.response.VendorEarningsResponse;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;

@RestController
@AllArgsConstructor
@RequestMapping("/api-vendor")
@Tag(name = "Vendor", description = "APIs for partner/vendor operations")
public class VendorController {

    private final ParcelServiceInterface parcelService;

    @GetMapping("/parcel-status-dashboard")
    @Operation(summary = "Vendor parcel dashboard", description = "Returns all vendor parcels grouped by destination station, with status summary. Optionally filter by status or search by parcel ID, receiver name, or receiver phone number.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Dashboard data retrieved successfully")
    })
    public VendorDashboardResponse getDashboard(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParcelStatus status) {
        return parcelService.getVendorDashboard(search, status);
    }

    @GetMapping("/earnings")
    @Operation(summary = "Vendor earnings dashboard", description = "Returns earnings breakdown: amount ready for payout, pending payout, failed deliveries, collection rate, per-station summary, and collected parcels awaiting vendor payment.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Earnings data retrieved successfully")
    })
    @TrackUserAction(action = "VENDOR_VIEW_EARNINGS", description = "Vendor viewed earnings dashboard")
    public VendorEarningsResponse getEarnings() {
        return parcelService.getVendorEarnings();
    }

    @GetMapping("/parcels")
    @Operation(summary = "List vendor parcels", description = "Returns a paginated list of the vendor's parcels. Filter by status, destination station (toOfficeId), or search by parcel ID, receiver name, or receiver phone number.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully")
    })
    public Page<Parcel> getVendorParcels(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(required = false) String toOfficeId,
            Pageable pageable) {
        return parcelService.getVendorParcels(search, status, toOfficeId, pageable);
    }

    @PostMapping("/parcels")
    @Operation(summary = "Submit a partner parcel", description = "Allows a vendor/partner to submit a parcel for delivery to a destination station")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel submitted successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Destination station not found"),
        @ApiResponse(responseCode = "403", description = "Access denied — vendor role required")
    })
    @TrackUserAction(action = "VENDOR_SUBMIT_PARCEL", description = "Vendor submitted a parcel for delivery")
    public Parcel addVendorParcel(@RequestBody @Valid VendorParcelRequest request) {
        return parcelService.addVendorParcel(request);
    }
}
