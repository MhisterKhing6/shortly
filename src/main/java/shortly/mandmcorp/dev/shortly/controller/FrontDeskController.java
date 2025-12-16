package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.DeliveryAssignmentResponse;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.service.rider.RiderServiceInterface;
import shortly.mandmcorp.dev.shortly.service.user.UserServiceInterface;



@RestController
@RequestMapping("/api-frontdesk")
@AllArgsConstructor
@Tag(name = "Front Desk Management", description = "APIs for front desk operations")
public class FrontDeskController {
    
    private final ParcelServiceInterface parcelService;
    private final RiderServiceInterface riderService;
    private final UserServiceInterface userService;

    @PostMapping("/parcel")
    @Operation(summary = "Add a new parcel", description = "Create a new parcel entry in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parcel data")
    })
    public ParcelResponse addParcel(@RequestBody @Valid ParcelRequest parcelRequest) {
        return parcelService.addParcel(parcelRequest);
    }

    @PutMapping("/parcel/{id}")
    @Operation(summary = "Update a parcel", description = "Update parcel details by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel updated successfully"),
        @ApiResponse(responseCode = "404", description = "Parcel not found")
    })
    public Parcel updateParcel(@PathVariable String id, @RequestBody @Valid ParcelUpdateRequest updateRequest) {
        return parcelService.updateParcel(id, updateRequest);
    }

    @GetMapping("/parcels")
    @Operation(summary = "Search parcels", description = "Search parcels with various filters and pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully")
    })
    public Page<Parcel> searchParcels(
            @RequestParam(required = false) Boolean isPOD,
            @RequestParam(required = false) Boolean isDelivered,
            @RequestParam(required = false) Boolean isParcelAssigned,
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String hasCalled,
            @RequestParam(required = false) String limit,
            @RequestParam(required = false) String page,
            Pageable pageable) {
        return parcelService.searchParcels(isPOD, isDelivered, isParcelAssigned, null, driverId, hasCalled, pageable, true);
    }

    @PostMapping("/assign-parcels")
    @Operation(summary = "Assign parcels to rider", description = "Assign multiple parcels to a specific rider")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels assigned successfully"),
        @ApiResponse(responseCode = "404", description = "Rider or parcel not found")
    })
    public UserResponse assignParcelsToRider(@RequestBody @Valid DeliveryAssignmentRequest assignmentRequest) {
        return riderService.assignParcelsToRider(assignmentRequest);
    }

    @PostMapping("/reconcilation-parcels")
    @Operation(summary = "Reconcile rider payments", description = "Mark multiple delivery assignments as paid for reconciliation")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliation completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid reconciliation data")
    })
    public UserResponse reconcilationRider(@RequestBody @Valid ReconcilationRiderRequest reconcilationRequest) {
        return riderService.reconcilation(reconcilationRequest);
    }

    @GetMapping("/rider/{riderId}/assignments")
    @Operation(summary = "Get rider assignments", description = "Get all delivery assignments for a specific rider with payment filter")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignments retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Rider not found")
    })
    public List<DeliveryAssignmentResponse> getRiderAssignments(
            @PathVariable String riderId,
            @RequestParam(defaultValue = "true") boolean payed) {
        return riderService.getRiderAssignmentsByRiderId(riderId, payed);
    }

    @GetMapping("/driver/{driverId}/parcels")
    @Operation(summary = "Get driver parcels", description = "Get all parcels for a specific driver with POD and inbound payment filters")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels retrieved successfully"),
        @ApiResponse(responseCode = "404", description = "Driver not found")
    })
    public List<Parcel> getDriverParcels(
            @PathVariable String driverId,
            @RequestParam(defaultValue = "true") boolean isPOD,
            @RequestParam(defaultValue = "false") String inboundPayed) {
        return parcelService.getParcelsByDriverId(driverId, isPOD, inboundPayed);
    }

    @GetMapping("/riders/office")
    @Operation(summary = "get a list of availagle rider  in an office", description = "An endpoint to get riders in an office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "shelf retrieved successfully"),
    })
    public List<User> getRiderOffice(@RequestParam(defaultValue = "true") boolean availability) {
        return userService.getRidersByOfficeId(availability);
    }
}
