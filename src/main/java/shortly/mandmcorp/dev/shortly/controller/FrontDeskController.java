package shortly.mandmcorp.dev.shortly.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import shortly.mandmcorp.dev.shortly.annotation.TrackUserAction;
import shortly.mandmcorp.dev.shortly.dto.request.AddAddressRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Address;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.service.office.OfficeServiceInterface;
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
    private final OfficeServiceInterface officeService;

    @PostMapping("/parcel")
    @Operation(summary = "Add a new parcel", description = "Create a new parcel entry in the system")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid parcel data")
    })
    @TrackUserAction(action = "ADD_PARCEL", description = "Front desk added a new parcel")
    public Parcel addParcel(@RequestBody @Valid ParcelRequest parcelRequest) {
        return parcelService.addParcel(parcelRequest);
    }

    @PutMapping("/parcel/{id}")
    @Operation(summary = "Update a parcel", description = "Update parcel details by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel updated successfully"),
        @ApiResponse(responseCode = "404", description = "Parcel not found")
    })
    @TrackUserAction(action = "UPDATE_PARCEL", description = "Front desk updated a parcel")
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
            @RequestParam(required = false) Boolean hasCalled,
            Pageable pageable) {
        return parcelService.searchParcels(isPOD, isDelivered, isParcelAssigned, null, driverId, hasCalled, pageable, true);
    }


    @GetMapping("/parcel-assignment")
    @Operation(summary = "return rider assignment", description = "Get delivery assignments by status with pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignments retrieved successfully")
    })
    public Page<DeliveryAssignments> orderAssignemnts(
        @RequestParam(defaultValue = "DELIVERED") DeliveryStatus status,
        Pageable pageable) {
        return riderService.getOrderAssignmentByStatus(status, pageable);
    }

    @GetMapping("/parcels/home-delivery")
    @Operation(summary = "Get office pickup parcels", description = "Get parcels available for pickup at the user's office (not home delivery, not delivered)")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Office pickup parcels retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @TrackUserAction(action = "VIEW_OFFICE_PICKUP_PARCELS", description = "User viewed office pickup parcels")
    public Page<Parcel> getOfficePickupParcels(Pageable pageable) {
        return parcelService.getHomeDeliveryParcels(pageable);
    }

    @GetMapping("/parcels-uncalled")
    @Operation(summary = "Get uncalled parcels", description = "Get parcels that have not been called in the user's office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Uncalled parcels retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @TrackUserAction(action = "VIEW_UNCALLED_PARCELS", description = "User viewed uncalled parcels")
    public Page<Parcel> getUncalledParcels(Pageable pageable) {
        return parcelService.getUncalledParcels(pageable);
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
    public List<DeliveryAssignments> getRiderAssignments(
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


  

    @GetMapping("/riders/assignments")
    @Operation(summary = "Get office rider assignments", description = "Get all delivery assignments in an office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Assignments retrieved successfully"),
    })
    public Page<DeliveryAssignments> getAssignments(
            @RequestParam(defaultValue = "false") boolean payed, Pageable pageable) {
        return riderService.getAcitveAssignments(pageable, payed);
    }

    @GetMapping("/riders/assignments/returned")
    @Operation(summary = "Get returned delivery assignments", description = "Get all returned delivery assignments in the user's office sorted by assignedAt descending")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Returned assignments retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @TrackUserAction(action = "VIEW_RETURNED_ASSIGNMENTS", description = "User viewed returned delivery assignments")
    public Page<DeliveryAssignments> getReturnedAssignments(Pageable pageable) {
        return riderService.getReturnedDeliveryAssignments(pageable);
    }

    @GetMapping("/reconciliation/stats")
    @Operation(summary = "Get reconciliation statistics", description = "Get reconciliation statistics for the user's office by time period (day, week, month, year, all)")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated")
    })
    @TrackUserAction(action = "VIEW_RECONCILIATION_STATS", description = "User viewed reconciliation statistics")
    public ReconciliationStatsResponse getReconciliationStats(
            @RequestParam(defaultValue = "day") String period) {
        return riderService.getReconciliationStats(period);
    }

    @GetMapping("/reconciliations/by-date")
    @Operation(summary = "Get reconciliations by date", description = "Get paginated reconciliations for a specific date filtered by createdAt or reconciledAt. Requires MANAGER or ADMIN role.")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Reconciliations retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "User not authenticated"),
        @ApiResponse(responseCode = "403", description = "User is not a manager or admin")
    })
    @TrackUserAction(action = "VIEW_RECONCILIATIONS_BY_DATE", description = "Manager/Admin viewed reconciliations by date")
    public Page<DeliveryAssignments> getReconciliationsByDate(
            @RequestParam Long date,
            @RequestParam(defaultValue = "false") boolean useReconciledAt,
            Pageable pageable) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !(auth.getPrincipal() instanceof User)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        
        User frontDesk = (User) auth.getPrincipal();
        return riderService.getReconciliationsByDate(date, frontDesk.getOfficeIds().get(0), useReconciledAt, pageable);
    }

    @DeleteMapping("/assignment/{assignmentId}/parcel/{parcelId}")
    @Operation(summary = "Remove parcel from assignment", description = "Remove a specific parcel from a delivery assignment")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcel removed from assignment successfully"),
        @ApiResponse(responseCode = "404", description = "Assignment or parcel not found")
    })
    @TrackUserAction(action = "REMOVE_PARCEL_FROM_ASSIGNMENT", description = "Front desk removed a parcel from an assignment")
    public UserResponse removeParcelFromAssignment(     
        @PathVariable String assignmentId,
        @PathVariable String parcelId) {
        return riderService.removeParcelFromAssignment(assignmentId, parcelId);
        }

        @GetMapping("/online-parcels/unpaid")
        @Operation(summary = "Get unpaid online parcels", description = "Get all unpaid online parcels in the user's office")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unpaid online parcels retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "VIEW_UNPAID_ONLINE_PARCELS", description = "Front desk viewed unpaid online parcels")
        public Page<Parcel> getUnpaidOnlineParcels(Pageable pageable) {
          return parcelService.getOnlineParcelsThatareMeantToBePayed(pageable);
        }

        @GetMapping("/addresses")
        @Operation(summary = "Get addresses stored", description = "get address by office")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "addresses saved "),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        public List<Address> search(@RequestParam(required = false) String name) {
            return officeService.getAllAddressesByName(name);
        }


        @PostMapping("/addresses")
        @Operation(summary = "save addresses ", description = "save office address")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "successfully saved address"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        public Address postAddress(@RequestBody @Valid AddAddressRequest request) {
            return officeService.addAddres(request);
        }

        @GetMapping("/driver-reconciliations/unpaid")
        @Operation(summary = "Get unpaid driver reconciliations",
                   description = "Returns paginated driver reconciliations that have not been paid for a given office.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unpaid driver reconciliations retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
        })
        public Page<DriverReconcilation> getUnpaidDriverReconciliations(Pageable pageable) {
            return riderService.getUnpaidDriverReconciliations(pageable);
        }

        @PutMapping("/driver-reconciliations/{reconciliationId}/pay")
        @Operation(summary = "Mark driver reconciliation as paid",
                   description = "Sets payed to true for the given driver reconciliation record.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver reconciliation marked as paid"),
            @ApiResponse(responseCode = "404", description = "Reconciliation not found or already paid"),
            @ApiResponse(responseCode = "401", description = "User not authenticated"),
            @ApiResponse(responseCode = "403", description = "Insufficient privileges")
        })
        public DriverReconcilation payDriverReconciliation(@PathVariable String reconciliationId) {
            return riderService.payDriverReconciliation(reconciliationId);
        }
    }

