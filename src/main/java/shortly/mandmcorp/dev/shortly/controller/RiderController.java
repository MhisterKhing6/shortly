package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.RiderStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.model.CancelationReason;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;

@AllArgsConstructor
@RestController
@RequestMapping("/api-rider")
public class RiderController {
    private final UserServiceInterface userService;
    private final RiderServiceInterface riderService;
    private final ParcelServiceInterface parcelService;

    @PutMapping("/rider-status")
    @Operation(summary = "Update rider status", description = "Update authenticated rider's status (BUSY, OFFLINE, READY, ON_TRIP)")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Rider status updated successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated or not a rider")
    })
    @TrackUserAction(action = "UPDATE_RIDER_STATUS", description = "Rider updated their status")
    public UserResponse updateRiderStatus(@RequestBody @Valid RiderStatusUpdateRequest statusRequest) {
        return userService.updateRiderStatus(statusRequest);
    }

    

    @GetMapping("/assignments")
    @Operation(summary = "Get rider assignments", description = "Get all assignments for authenticated rider")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignments retrieved successfully")
    })
    public List<DeliveryAssignments> getRiderAssignments(
            @RequestParam(defaultValue = "false") boolean onlyUndelivered) {
        return riderService.getRiderAssignments(onlyUndelivered);
    }

    @PutMapping("/assignments/{assignmentId}/status")
    @Operation(summary = "Update delivery status", description = "Update delivery assignment status")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Assignment not found")
    })
    @TrackUserAction(action = "UPDATE_DELIVERY_STATUS", description = "Rider updated delivery assignment status")
    public UserResponse updateDeliveryStatus(@PathVariable String assignmentId,
                                           @RequestBody @Valid DeliveryStatusUpdateRequest statusRequest) {
        return riderService.updateDeliveryStatus(assignmentId, statusRequest);
    }

    @PutMapping("/manager/assignments/{assignmentId}/status")
    @Operation(summary = "Update delivery status", description = "Update delivery assignment status by manger or admin")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Status updated successfully"),
        @ApiResponse(responseCode = "404", description = "Assignment not found")
    })
    public UserResponse adminUpdateAssignment(@PathVariable String assignmentId, 
                                           @RequestBody @Valid DeliveryStatusUpdateRequest statusRequest) {
        return riderService.managerUpdateDeliveryStatus(assignmentId, statusRequest);
    }

    @GetMapping("/search")
    @Operation(summary = "Search by receiver phone", description = "Search undelivered assignments by receiver phone number")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search results retrieved successfully")
    })
    public List<DeliveryAssignments> searchByReceiverPhone(
            @RequestParam String receiverPhone) {
        return riderService.searchByReceiverPhone(receiverPhone);
    }

    @GetMapping("/cancellation-reasons")
    @Operation(summary = "get a list of cancelation reason ", description = "An endpoint to get cancelation reasons")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "cancelation reason returns successfully"),
    })
    public List<CancelationReason> getCancelationReason() {
        return parcelService.cancleationReasons();
    }

    @GetMapping("/reconciliations")
    @Operation(summary = "Get rider reconciliations", description = "Get all reconciliations for authenticated rider sorted by creation date")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @TrackUserAction(action = "VIEW_RIDER_RECONCILIATIONS", description = "Rider viewed their reconciliations")
    public List<Reconcilations> getRiderReconciliations() {
        return riderService.getRiderReconciliations();
    }
}
