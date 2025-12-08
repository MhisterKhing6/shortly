package shortly.mandmcorp.dev.shortly.service.parcel.impl;


import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.ContaceRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;

@Service
@Slf4j
@AllArgsConstructor
public class ParcelServiceImplementation implements ParcelServiceInterface {
    
    private final ParcelRepository parcelRepository;
    private final ParcelMapper parcelMapper;
    private final ContaceRepository contactRepository;
    @Override
    public ParcelResponse addParcel(ParcelRequest parcelRequest) {
        log.info("Adding new parcel for sender: {}", parcelRequest.getSenderName());
        
        Contacts sender = parcelMapper.getOrCreateSender(parcelRequest.getSenderPhoneNumber(), parcelRequest.getSenderName());
        Contacts receiver = parcelMapper.getOrCreateReceiver(parcelRequest.getRecieverPhoneNumber(), parcelRequest.getReceiverName(), parcelRequest.getReceiverAddress());
        Contacts driver = parcelMapper.getOrCreateDriver(parcelRequest.getDriverPhoneNumber(), parcelRequest.getDriverName(), parcelRequest.getVehicleNumber());
        
        Parcel parcel = parcelMapper.toEntity(parcelRequest, driver, sender, receiver);
        
        // Set officeId from request or authenticated user
        if(parcelRequest.getOfficeId() != null) {
            parcel.setOfficeId(parcelMapper.getOfficeById(parcelRequest.getOfficeId()));
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if(auth != null && auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                parcel.setOfficeId(user.getOfficeId());
            }
        }
        log.debug("Mapped ParcelRequest to Parcel entity");
        
        Parcel savedParcel = parcelRepository.save(parcel);
        log.info("Parcel saved successfully with ID: {}", savedParcel.getParcelId());
        
        return parcelMapper.toResponse(savedParcel, driver, sender, receiver);
    }
    
    @Override
    public ParcelResponse updateParcel(String parcelId, ParcelUpdateRequest updateRequest) {
        log.info("Updating parcel with ID: {}", parcelId);
        
        Parcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new WrongCredentialsException("Parcel not found"));
        
        if(updateRequest.getDriverPhoneNumber() != null) {
            Contacts driver = parcelMapper.getOrCreateDriver(updateRequest.getDriverPhoneNumber(), updateRequest.getDriverName(), updateRequest.getVehicleNumber());
            parcel.setDriver(driver);
        }
        
        if(updateRequest.getSenderPhoneNumber() != null) {
            Contacts sender = parcelMapper.getOrCreateSender(updateRequest.getSenderPhoneNumber(), updateRequest.getSenderName());
            parcel.setSender(sender);
        }
        
        if(updateRequest.getReceiverAddress() != null) {
            parcel.getReceiver().setAddress(updateRequest.getReceiverAddress());
            contactRepository.save(parcel.getReceiver());
        }
        
        if(updateRequest.getParcelDescription() != null) parcel.setParcelDescription(updateRequest.getParcelDescription());
        parcel.setPOD(updateRequest.isPOD());
        parcel.setDelivered(updateRequest.isDelivered());
        parcel.setParcelAssigned(updateRequest.isParcelAssigned());
        parcel.setInboundCost(updateRequest.getInboundCost());
        parcel.setPickUpCost(updateRequest.getPickUpCost());
        parcel.setFragile(updateRequest.isFragile());
        parcel.setDeliveryCost(updateRequest.getDeliveryCost());
        parcel.setStorageCost(updateRequest.getStorageCost());
        if(updateRequest.getShelfNumber() != null) parcel.setShelfNumber(updateRequest.getShelfNumber());
        
        Parcel updatedParcel = parcelRepository.save(parcel);
        log.info("Parcel updated successfully with ID: {}", updatedParcel.getParcelId());
        
        return parcelMapper.toResponse(updatedParcel, parcel.getDriver(), parcel.getSender(), parcel.getReceiver());
    }
}
