package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.stereotype.Component;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.model.Parcel;

@Component
public class ParcelMapper {
    
    public Parcel toEntity(ParcelRequest request) {
        Parcel parcel = new Parcel();
        parcel.setSenderName(request.getSenderName());
        parcel.setSenderPhoneNumber(request.getSenderPhoneNumber());
        parcel.setReceiverName(request.getReceiverName());
        parcel.setReceiverAddress(request.getReceiverAddress());
        parcel.setRecieverPhoneNumber(request.getRecieverPhoneNumber());
        parcel.setParcelDescription(request.getParcelDescription());
        parcel.setDriverName(request.getDriverName());
        parcel.setDriverPhoneNumber(request.getDriverPhoneNumber());
        parcel.setPOD(request.isPOD());
        parcel.setDelivered(request.isDelivered());
        parcel.setParcelAssigned(request.isParcelAssigned());
        parcel.setInboundCost(request.getInboundCost());
        parcel.setPickUpCost(request.getPickUpCost());
        parcel.setFragile(request.isFragile());
        parcel.setDeliveryCost(request.getDeliveryCost());
        parcel.setStorageCost(request.getStorageCost());
        return parcel;
    }
    
    public ParcelResponse toResponse(Parcel parcel) {
        ParcelResponse response = new ParcelResponse();
        response.setParcelId(parcel.getParcelId());
        response.setSenderName(parcel.getSenderName());
        response.setSenderPhoneNumber(parcel.getSenderPhoneNumber());
        response.setReceiverName(parcel.getReceiverName());
        response.setReceiverAddress(parcel.getReceiverAddress());
        response.setRecieverPhoneNumber(parcel.getRecieverPhoneNumber());
        response.setParcelDescription(parcel.getParcelDescription());
        response.setDriverName(parcel.getDriverName());
        response.setDriverPhoneNumber(parcel.getDriverPhoneNumber());
        response.setPOD(parcel.isPOD());
        response.setDelivered(parcel.isDelivered());
        response.setParcelAssigned(parcel.isParcelAssigned());
        response.setInboundCost(parcel.getInboundCost());
        response.setPickUpCost(parcel.getPickUpCost());
        response.setFragile(parcel.isFragile());
        response.setDeliveryCost(parcel.getDeliveryCost());
        response.setStorageCost(parcel.getStorageCost());
        return response;
    }
}