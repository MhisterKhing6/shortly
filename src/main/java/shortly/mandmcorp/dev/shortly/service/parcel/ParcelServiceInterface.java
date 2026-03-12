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
     * Gets delivered parcels from yesterday where the call center has not yet spoken to the client.
     * Results are sorted by createdAt descending.
     *
     * @param pageable pagination parameters
     * @return Page of parcels delivered yesterday with hasCallCenterSpokenToClient = false
     */
    Page<Parcel> getYesterdayDeliveredParcelsNotCalledByCallCenter(Pageable pageable);

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
     * Returns call center statistics for parcels delivered yesterday:
     * total delivered, reached, unreachable, and not yet called.
     */
    shortly.mandmcorp.dev.shortly.dto.response.CallCenterStatsResponse getCallCenterStats();
}
