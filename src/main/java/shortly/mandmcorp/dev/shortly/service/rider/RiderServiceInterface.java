package shortly.mandmcorp.dev.shortly.service.rider;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentRequest;
import shortly.mandmcorp.dev.shortly.dto.request.DeliveryStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ReconcilationRiderRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ReconciliationStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;

public interface RiderServiceInterface {
    /**
     * Assigns multiple parcels to a rider and sends SMS notification.
     * 
     * @param assignmentRequest contains rider ID and list of parcel IDs
     * @return UserResponse with success message
     */
    UserResponse assignParcelsToRider(DeliveryAssignmentRequest assignmentRequest);
    
    /**
     * Retrieves delivery assignments for authenticated rider.
     * 
     * @param onlyUndelivered if true, returns only non-delivered assignments
     * @return List of delivery assignments with full parcel details
     */
    List<DeliveryAssignments> getRiderAssignments(boolean onlyUndelivered);
    
    /**
     * Updates delivery assignment status with automatic timestamp tracking.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new delivery status
     * @return UserResponse with success message
     */
    DeliveryAssignments updateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest);

    /**
     * Updates delivery assignment status with automatic timestamp tracking.
     * 
     * @param assignmentId assignment to update
     * @param statusRequest new delivery status
     * @return UserResponse with success message
     */
    DeliveryAssignments managerUpdateDeliveryStatus(String assignmentId, DeliveryStatusUpdateRequest statusRequest);
    
    /**
     * Searches rider's undelivered assignments by receiver phone number.
     *
     * @param receiverPhone receiver's phone number to search
     * @return List of matching undelivered assignments
     */
    List<DeliveryAssignments> searchByReceiverPhone(String receiverPhone);

    /**
     * Gets all delivery assignments for a specific rider with payment filter.
     * 
     * @param riderId rider ID to get assignments for
     * @param payed filter by payment status (default true)
     * @return List of delivery assignments with full parcel details
     */
    List<DeliveryAssignments> getRiderAssignmentsByRiderId(String riderId, boolean payed);
    
    /**
     * Marks multiple delivery assignments as paid for reconciliation.
     * Uses bulk operations for performance. Non-existent assignment IDs are silently skipped.
     * 
     * @param reconcilationRiderRequest contains rider ID and list of assignment IDs to reconcile
     * @return UserResponse with success message
     */
    UserResponse reconcilation(ReconcilationRiderRequest reconcilationRiderRequest);

    /**
     * get all assignment by status with pagination
     * @param status delivery status to filter
     * @param pageable pagination parameters
     * @return Page of delivery assignments
     */
    Page<DeliveryAssignments> getOrderAssignmentByStatus(DeliveryStatus status, Pageable pageable);

    /**
     * resend confirmation code to receiver
     * @param assignmentId
     * @return User response
     */
    UserResponse resendConfirmationCodeToReceiver(String assignmentId);

    /**
     * get all active assignment in an office
     * @param payed : a toggle for choosing weather we shold return all payed all not
     * @return
     */
    Page<DeliveryAssignments> getAcitveAssignments(Pageable pageable, boolean payed);

    /**
     * Gets all returned delivery assignments in the user's office.
     *
     * @param pageable pagination parameters
     * @return Page of returned delivery assignments sorted by assignedAt descending
     */
    Page<DeliveryAssignments> getReturnedDeliveryAssignments(Pageable pageable);

    /**
     * Gets reconciliation statistics for the user's office.
     *
     * @param period time period filter: "day", "week", "month", "year", or "all" (default: "day")
     * @return ReconciliationStatsResponse with completed and not completed counts and amounts
     */
    ReconciliationStatsResponse getReconciliationStats(String period);

    /**
     * Gets paginated reconciliations for the authenticated rider.
     *
     * @param pageable pagination information
     * @return Page of reconciliations sorted by createdAt descending
     */
    Page<Reconcilations> getRiderReconciliations(Pageable pageable);

    /**
     * Gets paginated reconciliations for all riders in the authenticated manager's office.
     *
     * @param pageable pagination information
     * @return Page of reconciliations sorted by createdAt descending
     */
    Page<Reconcilations> getOfficeReconciliations(Pageable pageable);

    /**
     * Gets paginated reconciliations for a specific date.
     * Filters by either createdAt or reconciledAt timestamp.
     *
     * @param date the date in milliseconds (epoch timestamp)
     * @param useReconciledAt if true, filters by reconciledAt; otherwise uses createdAt
     * @param officeId the office ID to filter reconciliations for
     * @param pageable pagination information
     * @return Page of reconciliations for the specified date
     */
    Page<DeliveryAssignments> getReconciliationsByDate(Long date, String officeId, boolean useReconciledAt, Pageable pageable);

    /**
     * Updates a delivery assignment and syncs changes to Parcel database and ParcelInfo.
     *
     * @param updateRequest the update request containing assignment data
     * @return UserResponse with success message
     */
    UserResponse updateDeliveryAssignment(shortly.mandmcorp.dev.shortly.dto.request.DeliveryAssignmentUpdateRequest updateRequest);            // Fetch and update all parcels using parcelIds from embedded ParcelInfo list

     /**
     * Remove parcel from assignment.
     *
     * @param assignmentId the assignment ID
     * @param parcelId the parcel ID to remove
     * @return UserResponse with success message
     */
    UserResponse removeParcelFromAssignment(String assignmentId, String parcelId);

    /**
     * Gets paginated unpaid driver reconciliations for the logged-in user's office.
     *
     * @param pageable pagination parameters
     * @return Page of unpaid DriverReconcilation records
     */
    Page<DriverReconcilation> getUnpaidDriverReconciliations(Pageable pageable);

    /**
     * Marks a driver reconciliation as paid.
     *
     * @param reconciliationId the ID of the reconciliation to mark as paid
     * @return the updated DriverReconcilation
     */
    DriverReconcilation payDriverReconciliation(String reconciliationId);

    /**
     * Marks a list of DriverAssignments as paid.
     * whoPayedDriverName and whoPayedDriverPhoneNumber are taken from the logged-in user.
     *
     * @param assignmentIds list of DriverAssignment IDs to mark as paid
     * @return UserResponse with success message
     */
    UserResponse payDriverAssignments(java.util.List<String> assignmentIds);

    /**
     * Returns paginated unpaid DriverAssignments for the logged-in user's office,
     * sorted by driverPhoneNumber ascending.
     * If driverPhoneNumber is provided, filters to only that driver's assignments.
     */
    Page<shortly.mandmcorp.dev.shortly.model.DriverAssignment> getUnpaidDriverAssignments(String driverPhoneNumber, Pageable pageable);

    /**
     * Creates a fuel request for the authenticated rider.
     * Sets riderInfo and officeId from the logged-in user.
     *
     * @param request fuel request details
     * @return the saved FuelRequest
     */
    shortly.mandmcorp.dev.shortly.model.FuelRequest createFuelRequest(shortly.mandmcorp.dev.shortly.dto.request.FuelRequestDto request);

    /**
     * Partially updates a fuel request. Only non-null fields in the request are applied.
     *
     * @param fuelRequestId the ID of the fuel request to update
     * @param request       fields to update (any field may be null to skip it)
     * @return the updated FuelRequest
     */
    shortly.mandmcorp.dev.shortly.model.FuelRequest updateFuelRequest(String fuelRequestId, shortly.mandmcorp.dev.shortly.dto.request.FuelRequestUpdateDto request);

    /**
     * Returns fuel request statistics: total, approved, pending, and rejected counts.
     */
    shortly.mandmcorp.dev.shortly.dto.response.FuelRequestStatsResponse getFuelRequestStats();

    /**
     * Returns all fuel requests paginated.
     *
     * @param pageable pagination parameters
     * @return Page of FuelRequest
     */
    Page<shortly.mandmcorp.dev.shortly.model.FuelRequest> getFuelRequests(Pageable pageable);

    /**
     * Returns paginated fuel requests for the logged-in rider, sorted by createdAt descending.
     *
     * @param pageable pagination parameters
     * @return Page of FuelRequest belonging to the authenticated rider
     */
    Page<shortly.mandmcorp.dev.shortly.model.FuelRequest> getRiderFuelRequests(Pageable pageable);

}