package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.CallCenterUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CallCenterStatsResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;

@RestController
@AllArgsConstructor
@RequestMapping("/api-call-center")
@Tag(name = "Call Center Management", description = "APIs for call center operations")
public class CallCenterController {

    private final ParcelServiceInterface parcelService;

    @GetMapping("/parcels/uncalled")
    @Operation(summary = "Get parcels not called by call center",
               description = "Returns paginated parcels that have hasCallCenterSpokenToClient set to false or null, sorted by createdAt descending")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_UNCALLED_PARCELS", description = "Call center agent viewed parcels not yet called")
    public Page<Parcel> getUncalledParcels(Pageable pageable) {
        return parcelService.getUncalledCallCenterParcels(pageable);
    }

    @PutMapping("/parcels/{parcelId}/call-outcome")
    @Operation(summary = "Update call center outcome for a parcel",
               description = "Updates the callOutCome for a parcel. If callOutCome is REACHED, hasCallCenterSpokenToClient is automatically set to true.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel updated successfully"),
        @ApiResponse(responseCode = "404", description = "Parcel not found"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "UPDATE_CALL_CENTER_OUTCOME", description = "Call center agent updated call outcome for a parcel")
    public Parcel updateCallCenterOutcome(@PathVariable String parcelId,
                                          @RequestBody @Valid CallCenterUpdateRequest request) {
        return parcelService.updateCallCenterOutcome(parcelId, request);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get call center statistics",
               description = "Returns statistics for parcels delivered yesterday: total delivered, reached, unreachable, and not yet called.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "Insufficient privileges")
    })
    @TrackUserAction(action = "VIEW_CALL_CENTER_STATS", description = "Call center agent viewed call center statistics")
    public CallCenterStatsResponse getCallCenterStats() {
        return parcelService.getCallCenterStats();
    }
}
