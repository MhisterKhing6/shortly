package shortly.mandmcorp.dev.shortly.service.parcel.impl;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.mongodb.DBRef;

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
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;
    private final ShelfRepository shelfRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER', 'ADMIN')")
    public ParcelResponse addParcel(ParcelRequest parcelRequest) {
        Contacts sender = parcelMapper.getOrCreateSender(
                parcelRequest.getSenderPhoneNumber(),
                parcelRequest.getSenderName()
        );
        Contacts receiver = parcelMapper.getOrCreateReceiver(
                parcelRequest.getRecieverPhoneNumber(),
                parcelRequest.getReceiverName(),
                parcelRequest.getReceiverAddress()
        );
        Contacts driver = parcelMapper.getOrCreateDriver(
                parcelRequest.getDriverPhoneNumber(),
                parcelRequest.getDriverName(),
                parcelRequest.getVehicleNumber()
        );

        Parcel parcel = parcelMapper.toEntity(parcelRequest, driver, sender, receiver, null);

        if (parcelRequest.getOfficeId() != null) {
            Office office = officeRepository.findById(parcelRequest.getOfficeId())
                    .orElseThrow(() -> new EntityNotFound("Office not found"));
            parcel.setOfficeId(office.getId());
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                parcel.setOfficeId(user.getOfficeId());
            }
        }

        Shelf shelf = shelfRepository.findById(parcelRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));

        if( !shelf.getOffice().getId().equals(parcel.getOfficeId())) {
            throw new WrongCredentialsException("Shelf does not belong to the specified office");
        }
        parcel.setShelfName(shelf.getName());
        parcel.setShelfId(shelf.getId());
        Parcel saved = parcelRepository.save(parcel);
        return parcelMapper.toResponse(saved, driver, sender, receiver);
    }

    @Override
    public Parcel updateParcel(String parcelId, ParcelUpdateRequest updateRequest) {
    Parcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new WrongCredentialsException("Parcel not found"));

    if (updateRequest.getDriverPhoneNumber() != null) {
        parcel.setDriverPhoneNumber(updateRequest.getDriverPhoneNumber());
        parcel.setDriverName(updateRequest.getDriverName());
        parcel.setVehicleNumber(updateRequest.getVehicleNumber());
    }

    if (updateRequest.getSenderPhoneNumber() != null) {
        parcel.setSenderPhoneNumber(updateRequest.getSenderPhoneNumber());
        parcel.setSenderName(updateRequest.getSenderName());
    }

    if (updateRequest.getReceiverAddress() != null) {
        parcel.setReceiverAddress(updateRequest.getReceiverAddress());
    }
    if (updateRequest.getReceiverName() != null) {
        parcel.setReceiverName(updateRequest.getReceiverName());
    }
    if (updateRequest.getRecieverPhoneNumber() != null) {
        parcel.setRecieverPhoneNumber(updateRequest.getRecieverPhoneNumber());
    }

    if (updateRequest.getParcelDescription() != null) {
        parcel.setParcelDescription(updateRequest.getParcelDescription());
    }
    parcel.setPOD(updateRequest.isPOD());
    parcel.setDelivered(updateRequest.isDelivered());
    parcel.setParcelAssigned(updateRequest.isParcelAssigned());
    parcel.setInboundCost(updateRequest.getInboundCost());
    parcel.setPickUpCost(updateRequest.getPickUpCost());
    parcel.setFragile(updateRequest.isFragile());
    parcel.setDeliveryCost(updateRequest.getDeliveryCost());
    parcel.setStorageCost(updateRequest.getStorageCost());

    if (updateRequest.getShelfNumber() != null) {
        parcel.setShelfId(updateRequest.getShelfNumber());
        Shelf shelf = shelfRepository.findById(updateRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));
        parcel.setShelfName(shelf.getName());
    }

    Parcel updated = parcelRepository.save(parcel);

    return updated;
    }
    

    @Override
    public Page<Parcel> searchParcels(
        Boolean isPOD,
        Boolean isDelivered,
        Boolean isParcelAssigned,
        String officeId,
        String driverPhoneNumber,
        String hasCalled,
        Pageable pageable,
        boolean isFrontDesk) {

    if (isFrontDesk) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (user.getOfficeId() != null) {
                officeId = user.getOfficeId();
            }
        }
    }

    Query query = new Query();
    List<Criteria> criteria = new ArrayList<>();

    if (isPOD != null) {
        criteria.add(Criteria.where("isPOD").is(isPOD));
    }

    if (isDelivered != null) {
        criteria.add(Criteria.where("isDelivered").is(isDelivered));
    }

    if (isParcelAssigned != null) {
        criteria.add(Criteria.where("isParcelAssigned").is(isParcelAssigned));
    }

    if (hasCalled != null) {
        criteria.add(Criteria.where("hasCalled").is(hasCalled));
    }

    if (officeId != null) {
        criteria.add(Criteria.where("officeId").is(officeId));
    }

    if (driverPhoneNumber != null) {
        criteria.add(Criteria.where("driverPhoneNumber").is(driverPhoneNumber));
    }

    if (!criteria.isEmpty()) {
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    query.with(pageable);

    long total = mongoTemplate.count(query, Parcel.class);
    List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

    return new PageImpl<>(parcels, pageable, total);
}

    /**
     * Extracts the ObjectId (as hex string) from a DBRef or a nested Document representation.
     */
    private String extractRefId(Object ref) {
        if (ref == null) {
            return null;
        }

        // Case 1: Legacy DBRef object
        if (ref instanceof DBRef dbRef) {
            Object id = dbRef.getId();
            if (id instanceof ObjectId objectId) {
                return objectId.toHexString();
            }
            return id.toString();
        }

        // Case 2: Nested Document { "$ref": "contacts", "$id": ObjectId(...) }
        if (ref instanceof Document document) {
            Object idObj = document.get("$id");
            if (idObj instanceof ObjectId objectId) {
                return objectId.toHexString();
            }
            if (idObj != null) {
                return idObj.toString();
            }
        }

        return null;
    }

    public UserResponse changeOffice(String officeId) {
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new EntityNotFound("Office not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        user.setOfficeId(office.getId());
        userRepository.save(user);

        return new UserResponse("Office changed successfully", user.getPhoneNumber());
    }

    @Override
    public List<Parcel> getParcelsByDriverId(String driverId, boolean isPOD, String inboundPayed) {
       
        return null;
        
    }
}