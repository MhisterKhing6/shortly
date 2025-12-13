package shortly.mandmcorp.dev.shortly.service.parcel.impl;


import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.ContaceRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.ShelfRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.parcel.ParcelServiceInterface;
import shortly.mandmcorp.dev.shortly.utils.ParcelMapper;


@Service
@Slf4j
@AllArgsConstructor
public class ParcelServiceImplementation implements ParcelServiceInterface {
    
    private final ParcelRepository parcelRepository;
    private final ParcelMapper parcelMapper;
    private final ContaceRepository contactRepository;
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;
    private final ShelfRepository shelfRepository;

    @Override
    public ParcelResponse addParcel(ParcelRequest parcelRequest) {
        log.info("Adding new parcel for sender: {}", parcelRequest.getSenderName());
        
        Contacts sender = parcelMapper.getOrCreateSender(parcelRequest.getSenderPhoneNumber(), parcelRequest.getSenderName());
        Contacts receiver = parcelMapper.getOrCreateReceiver(parcelRequest.getRecieverPhoneNumber(), parcelRequest.getReceiverName(), parcelRequest.getReceiverAddress());
        Contacts driver = parcelMapper.getOrCreateDriver(parcelRequest.getDriverPhoneNumber(), parcelRequest.getDriverName(), parcelRequest.getVehicleNumber());
        
        Parcel parcel = parcelMapper.toEntity(parcelRequest, driver, sender, receiver, null);
        
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

        Shelf shelf = shelfRepository.findById(parcelRequest.getShelfNumber())
            .orElseThrow(() -> new EntityNotFound("Shelf not found"));

        parcel.setShelf(shelf);
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
        if(updateRequest.getShelfNumber() != null) {
            Shelf shelf = shelfRepository.findById(updateRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));
            parcel.setShelf(shelf);
        } 
        
        Parcel updatedParcel = parcelRepository.save(parcel);
        log.info("Parcel updated successfully with ID: {}", updatedParcel.getParcelId());
        
        return parcelMapper.toResponse(updatedParcel, parcel.getDriver(), parcel.getSender(), parcel.getReceiver());
    }

    @Override
    public Page<ParcelResponse> searchParcels(Boolean isPOD, Boolean isDelivered, Boolean isParcelAssigned, 
                                            String officeId, String driverId, String hasCalled, Pageable pageable, boolean isFrontDesk) {
        log.info("Searching parcels with filters - isPOD: {}, isDelivered: {}, isAssigned: {}, officeId: {}, driverId: {}, hasCalled: {}", 
                isPOD, isDelivered, isParcelAssigned, officeId, driverId, hasCalled);
        
        Page<Parcel> parcels;

        if(isFrontDesk) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if(auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                Office userOffice = user.getOfficeId();
                if(userOffice != null) {
                    officeId = userOffice.getId();
                }
            }
        }
        
        if(isPOD != null) {
            parcels = parcelRepository.findByIsPOD(isPOD, pageable);
        } else if(isDelivered != null) {
            parcels = parcelRepository.findByIsDelivered(isDelivered, pageable);
        } else if(isParcelAssigned != null) {
            parcels = parcelRepository.findByIsParcelAssigned(isParcelAssigned, pageable);
        } else if(officeId != null) {
            Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new EntityNotFound("Office not found"));
            parcels = parcelRepository.findByOfficeId(office, pageable);
        } else if(driverId != null) {
            Contacts driver = contactRepository.findById(driverId)
                .orElseThrow(() -> new EntityNotFound("Driver not found"));
            parcels = parcelRepository.findByDriver(driver, pageable);
        } else if(hasCalled != null) {
            parcels = parcelRepository.findByHasCalled(hasCalled, pageable);
        } else {
            parcels = parcelRepository.findAllParcels(pageable);
        }
        
        return parcels.map(parcel -> parcelMapper.toResponse(parcel, parcel.getDriver(), parcel.getSender(), parcel.getReceiver()));
    }

    public UserResponse changeOffice(String officeId) {
        log.info("Changing office to: {}", officeId);
        Office office = officeRepository.findById(officeId)
            .orElseThrow(() -> new EntityNotFound("Office not found"));
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth != null && auth.getPrincipal() instanceof User) {
            User user = (User) auth.getPrincipal();
            user.setOfficeId(office);
            userRepository.save(user);
            return new UserResponse("Office changed successfully", user.getPhoneNumber());
        } else {
            throw new WrongCredentialsException("User not authenticated");
        }
    }

    /**
     * Gets all parcels for a specific driver with POD and inbound payment filters.
     * Returns parcels with driver, sender, and receiver resolved to objects.
     * 
     * @param driverId driver ID to get parcels for
     * @param isPOD filter by POD status
     * @param inboundPayed filter by inbound payment status
     * @return List of parcels with resolved contact objects
     * @throws EntityNotFound if driver not found
     */
    @Override
    public java.util.List<ParcelResponse> getParcelsByDriverId(String driverId, boolean isPOD, String inboundPayed) {
        if(!contactRepository.existsById(driverId)) {
            throw new EntityNotFound("Driver not found");
        }
        
        java.util.List<Parcel> parcels = parcelRepository.findByDriverIdAndIsPODAndInboudPayed(driverId, isPOD, inboundPayed);
        return parcels.stream()
            .map(parcel -> parcelMapper.toResponse(parcel, parcel.getDriver(), parcel.getSender(), parcel.getReceiver()))
            .collect(java.util.stream.Collectors.toList());
    }

}
