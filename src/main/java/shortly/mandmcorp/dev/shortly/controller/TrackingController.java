package shortly.mandmcorp.dev.shortly.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.TrackingAssignDriverRequest;
import shortly.mandmcorp.dev.shortly.dto.request.TrackingStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.PublicTrackingResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.service.tracking.ParcelTrackingServiceInterface;

@RestController
@RequestMapping("/api-tracking")
@AllArgsConstructor
@Tag(name = "Parcel Tracking", description = "APIs for parcel tracking with role-based views")
public class TrackingController {

    private final ParcelTrackingServiceInterface trackingService;

    @GetMapping("/{parcelId}/track")
    @Operation(summary = "Public parcel tracking", description = "Track a parcel without authentication. Returns limited public view.")
    public PublicTrackingResponse trackParcel(@PathVariable String parcelId) {
        return trackingService.getPublicTracking(parcelId);
    }

    @GetMapping("/{parcelId}")
    @Operation(summary = "Role-aware parcel view", description = "Get parcel details based on authenticated user's role")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Object> getParcelDetails(@PathVariable String parcelId) {
        return ResponseEntity.ok(trackingService.getRoleAwareTracking(parcelId));
    }

    @PatchMapping("/{parcelId}/status")
    @Operation(summary = "Update parcel status", description = "Update parcel delivery status (RIDER, ADMIN, MANAGER only)")
    @SecurityRequirement(name = "Bearer Authentication")
    @TrackUserAction(action = "UPDATE_PARCEL_STATUS", description = "Updated parcel tracking status")
    public ResponseEntity<Parcel> updateStatus(@PathVariable String parcelId,
                                               @RequestBody @Valid TrackingStatusUpdateRequest request) {
        return ResponseEntity.ok(trackingService.updateTrackingStatus(parcelId, request));
    }

    @PatchMapping("/{parcelId}/assign")
    @Operation(summary = "Assign driver to parcel", description = "Assign a rider to a parcel (FRONTDESK, ADMIN, MANAGER only)")
    @SecurityRequirement(name = "Bearer Authentication")
    @TrackUserAction(action = "ASSIGN_DRIVER_TRACKING", description = "Assigned a driver via tracking endpoint")
    public ResponseEntity<Parcel> assignDriver(@PathVariable String parcelId,
                                               @RequestBody @Valid TrackingAssignDriverRequest request) {
        return ResponseEntity.ok(trackingService.assignDriverToParcel(parcelId, request));
    }
}
