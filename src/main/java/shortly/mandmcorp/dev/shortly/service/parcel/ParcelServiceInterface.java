package shortly.mandmcorp.dev.shortly.service.parcel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.model.Parcel;

public interface ParcelServiceInterface {
    Parcel addParcel(ParcelRequest parcelRequest);
    Parcel updateParcel(String parcelId, ParcelUpdateRequest updateRequest);
    Page<Parcel> searchParcels(Boolean isPOD, Boolean isDelivered, Boolean isParcelAssigned,
                                      String officeId, String driverId, Boolean hasCalled, Pageable pageable, boolean isFrontDesk);
    
    /**
     * Gets all parcels for a specific driver with POD and inbound payment filters.
     * 
     * @param driverId driver ID to get parcels for
     * @param isPOD filter by POD status (default true)
     * @param inboundPayed filter by inbound payment status (default false)
     * @return List of parcels with driver, sender, and receiver resolved
     */
    java.util.List<Parcel> getParcelsByDriverId(String driverId, boolean isPOD, String inboundPayed);

    
   /**
    * Gets online parcels that are meant to be payed (isItemOwnerPaid = false).
     *
    * @return onlin parcels that are meant to be payed (isItemOwnerPaid = false)
    */
    Page<Parcel> getOnlineParcelsThatareMeantToBePayed(Pageable pageable);

    /**
     * Gets parcels available for office pickup (not home delivery, not delivered, in user's office).
     *
     * @param pageable pagination parameters
     * @return Page of parcels that are in the user's office, not delivered, and not for home delivery
     */
    Page<Parcel> getHomeDeliveryParcels(Pageable pageable);

    /**
     * Gets parcels that have not been called in the user's office.
     *
     * @param pageable pagination parameters
     * @return Page of parcels that are uncalled in the user's office
     */
    Page<Parcel> getUncalledParcels(Pageable pageable);

    /**
     * Gets parcels where the call center has not yet spoken to the client.
     * Results are sorted by createdAt descending.
     *
     * @param pageable pagination parameters
     * @return Page of parcels with hasCallCenterSpokenToClient = false or null
     */
    Page<Parcel> getUncalledCallCenterParcels(String officeId, Pageable pageable);

    /**
     * Updates the call center outcome for a parcel. If callOutCome is REACHED,
     * hasCallCenterSpokenToClient is set to true.
     *
     * @param parcelId the ID of the parcel to update
     * @param request  the call center update request containing the call outcome
     * @return the updated Parcel
     */
    Parcel updateCallCenterOutcome(String parcelId, shortly.mandmcorp.dev.shortly.dto.request.CallCenterUpdateRequest request);

    /**
     * Returns paginated ParcelSystemLog entries. Optionally filtered by officeId and/or parcelId.
     * If neither is provided, returns logs from all offices.
     */
    org.springframework.data.domain.Page<shortly.mandmcorp.dev.shortly.model.ParcelSystemLog> getParcelSystemLogs(String officeId, String parcelId, org.springframework.data.domain.Pageable pageable);

    /**
     * Marks a parcel as picked up/delivered and saves a ParcelSystemLog entry.
     * frontDeskPersonellName, frontDeskPersonellPhoneNumber and officeId are taken from the logged-in user.
     */
    shortly.mandmcorp.dev.shortly.model.ParcelSystemLog pickedUp(shortly.mandmcorp.dev.shortly.dto.request.PickedUpRequest request);

    /**
     * Returns call center statistics for parcels delivered yesterday:
     * total delivered, reached, unreachable, and not yet called.
     */
    shortly.mandmcorp.dev.shortly.dto.response.CallCenterStatsResponse getCallCenterStats();

    /**
     * Returns paginated parcels that are delivered and have not been called (hasCalled=false),
     * filtered by the logged-in user's officeId.
     */
    Page<Parcel> getDeliveredUncalledParcels(String officeId, Pageable pageable);

    /**
     * Returns paginated parcels that are NOT delivered and have not been called (hasCalled=false),
     * filtered by the given officeId.
     */
    Page<Parcel> getNotDeliveredUncalledParcels(String officeId, Pageable pageable);

    /**
     * Returns call stats for a specific caller by phone number.
     * period = "all" returns all-time stats; period = "month" returns stats for the current month.
     */
    shortly.mandmcorp.dev.shortly.dto.response.CallerStatsResponse getCallerStats(String callerPhoneNumber, String period);

    /**
     * Returns paginated online parcels (typeofParcel=ONLINE) in transit to the logged-in user's office
     * (to.officeId == user's officeId, hasArrivedAtOffice=false).
     */
    Page<Parcel> getTransferParcelsInTransit(Pageable pageable);

    /**
     * Returns paginated online parcels (typeofParcel=ONLINE) that have arrived at the logged-in user's office
     * (to.officeId == user's officeId, hasArrivedAtOffice=true).
     */
    Page<Parcel> getOnlineParcelsArrived(Pageable pageable);

    /**
     * Returns paginated online parcels (typeofParcel=ONLINE) dispatched from the logged-in user's office
     * that have not yet arrived at the destination (from.officeId == user's officeId, hasArrivedAtOffice=false).
     */
    Page<Parcel> getTransferOutgoing(Pageable pageable);

    /**
     * Marks a transfer parcel as arrived at the destination office (hasArrivedAtOffice=true).
     * Sets officeId to the logged-in user's officeId, shelfId, and resolves shelfName from the shelf record.
     */
    Parcel markParcelAsArrived(String parcelId, String shelfId);

    /**
     * Deletes a parcel by its ID.
     *
     * @param parcelId the ID of the parcel to delete
     */
    void deleteParcel(String parcelId);
}
