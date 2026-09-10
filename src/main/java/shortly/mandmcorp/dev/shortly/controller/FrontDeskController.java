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
import shortly.mandmcorp.dev.shortly.dto.request.FuelRequestUpdateDto;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelReceivedRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.PickedUpRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.FuelRequestStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Address;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.FuelRequest;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.ParcelSystemLog;
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

    @PostMapping("/parcels/received")
    @Operation(summary = "Mark parcels as received", description = "Marks a list of parcels as received and sets their office to the logged-in user's office")
    @SecurityRequirement(name = "Bearer Authentication")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Parcels marked as received"),
        @ApiResponse(responseCode = "404", description = "One or more parcel IDs not found")
    })
    @TrackUserAction(action = "MARK_PARCELS_RECEIVED", description = "Front desk marked parcels as received")
    public List<Parcel> markParcelsAsReceived(@RequestBody @Valid ParcelReceivedRequest request) {
        return parcelService.markParcelsAsReceived(request);
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
            @RequestParam(required = false) String search,
            Pageable pageable) {
        return parcelService.searchParcels(isPOD, isDelivered, isParcelAssigned, null, driverId, hasCalled, search, pageable, true);
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
        if (auth == null || !(auth.getPrincipal() instanceof User frontDesk)) {
            throw new WrongCredentialsException("User not authenticated");
        }
        if (frontDesk.getOfficeIds() == null || frontDesk.getOfficeIds().isEmpty()) {
            throw new WrongCredentialsException("User has no associated office");
        }
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

        @PostMapping("/parcel/picked-up")
        @Operation(summary = "Mark parcel as picked up", description = "Marks a parcel as delivered/picked up and saves a system log entry. Front desk name, phone, and office are taken from the logged-in user.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parcel marked as picked up successfully"),
            @ApiResponse(responseCode = "404", description = "Parcel not found"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "PARCEL_PICKED_UP", description = "Front desk marked a parcel as picked up")
        public ParcelSystemLog pickedUp(@RequestBody @Valid PickedUpRequest request) {
            return parcelService.pickedUp(request);
        }

        @GetMapping("/driver-assignments/unpaid")
        @Operation(summary = "Get unpaid driver assignments", description = "Returns paginated unpaid driver assignments for the logged-in user's office, sorted by driver phone number.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Unpaid driver assignments retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "VIEW_UNPAID_DRIVER_ASSIGNMENTS", description = "Front desk viewed unpaid driver assignments")
        public Page<shortly.mandmcorp.dev.shortly.model.DriverAssignment> getUnpaidDriverAssignments(
                @RequestParam(required = false) String driverPhoneNumber,
                Pageable pageable) {
            return riderService.getUnpaidDriverAssignments(driverPhoneNumber, pageable);
        }

        @PutMapping("/driver-assignments/pay")
        @Operation(summary = "Pay driver assignments", description = "Marks a list of driver assignments as paid. The logged-in user is recorded as who paid.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Driver assignments marked as paid successfully"),
            @ApiResponse(responseCode = "404", description = "No assignments found for the provided IDs"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "PAY_DRIVER_ASSIGNMENTS", description = "Front desk marked driver assignments as paid")
        public UserResponse payDriverAssignments(@RequestBody List<String> assignmentIds) {
            return riderService.payDriverAssignments(assignmentIds);
        }

        @GetMapping("/fuel-requests")
        @Operation(summary = "Get fuel requests", description = "Returns paginated fuel requests, optionally filtered by status")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fuel requests retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        public Page<FuelRequest> getFuelRequests(Pageable pageable) {
            return riderService.getFuelRequests(pageable);
        }

        @GetMapping("/fuel-request/stats")
        @Operation(summary = "Get fuel request statistics", description = "Returns total, approved, pending, and rejected fuel request counts")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        public FuelRequestStatsResponse getFuelRequestStats() {
            return riderService.getFuelRequestStats();
        }

        @PutMapping("/fuel-request/{fuelRequestId}")
        @Operation(summary = "Update a fuel request", description = "Partially updates a fuel request — only fields provided in the request body are applied")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fuel request updated successfully"),
            @ApiResponse(responseCode = "404", description = "Fuel request not found"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "UPDATE_FUEL_REQUEST", description = "Front desk updated a fuel request")
        public FuelRequest updateFuelRequest(@PathVariable String fuelRequestId,
                @RequestBody FuelRequestUpdateDto request) {
            return riderService.updateFuelRequest(fuelRequestId, request);
        }

        @DeleteMapping("/parcel/{parcelId}")
        @Operation(summary = "Delete a parcel", description = "Deletes a parcel by its ID")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parcel deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Parcel not found"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "DELETE_PARCEL", description = "Front desk deleted a parcel")
        public void deleteParcel(@PathVariable String parcelId) {
            parcelService.deleteParcel(parcelId);
        }

        @GetMapping("/parcels/transfer/in-transit")
        @Operation(summary = "Get transfer parcels in transit", description = "Returns paginated transfer parcels (parcelTransfer=true) that are on their way to the logged-in user's office (hasArrivedAtOffice=false).")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Transfer parcels in transit retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "VIEW_TRANSFER_PARCELS_IN_TRANSIT", description = "Front desk viewed transfer parcels in transit to their office")
        public Page<Parcel> getTransferParcelsInTransit(Pageable pageable) {
            return parcelService.getTransferParcelsInTransit(pageable);
        }

        @GetMapping("/parcels/online/arrived")
        @Operation(summary = "Get online parcels arrived", description = "Returns paginated online parcels (typeofParcel=ONLINE) that have arrived at the logged-in user's office (hasArrivedAtOffice=true).")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Online parcels arrived retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "VIEW_ONLINE_PARCELS_ARRIVED", description = "Front desk viewed online parcels arrived at their office")
        public Page<Parcel> getOnlineParcelsArrived(Pageable pageable) {
            return parcelService.getOnlineParcelsArrived(pageable);
        }

        @GetMapping("/parcels/transfer/outgoing")
        @Operation(summary = "Get outgoing transfer parcels", description = "Returns paginated transfer parcels (parcelTransfer=true) dispatched from the logged-in user's office that have not yet arrived at their destination (hasArrivedAtOffice=false).")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Outgoing transfer parcels retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "VIEW_TRANSFER_PARCELS_OUTGOING", description = "Front desk viewed outgoing transfer parcels from their office")
        public Page<Parcel> getTransferOutgoing(Pageable pageable) {
            return parcelService.getTransferOutgoing(pageable);
        }

        @PutMapping("/parcels/transfer/{parcelId}/arrived")
        @Operation(summary = "Mark transfer parcel as arrived", description = "Sets hasArrivedAtOffice=true on a transfer parcel, sets officeId to the logged-in user's office, and resolves shelf name from the given shelfId.")
        @SecurityRequirement(name = "Bearer Authentication")
        @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Parcel marked as arrived successfully"),
            @ApiResponse(responseCode = "404", description = "Parcel or shelf not found"),
            @ApiResponse(responseCode = "401", description = "User not authenticated")
        })
        @TrackUserAction(action = "MARK_TRANSFER_PARCEL_ARRIVED", description = "Front desk marked a transfer parcel as arrived at the office")
        public Parcel markParcelAsArrived(@PathVariable String parcelId,
                @RequestParam(required = true) String shelfId) {
            return parcelService.markParcelAsArrived(parcelId, shelfId);
        }


    }

