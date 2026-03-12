package shortly.mandmcorp.dev.shortly.service.parcel.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.CallCenterUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.CallCenterStatsResponse;
import shortly.mandmcorp.dev.shortly.dto.response.UserResponse;
import shortly.mandmcorp.dev.shortly.enums.CallCenterCallOutCome;
import shortly.mandmcorp.dev.shortly.enums.ParcelTypes;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.exceptions.WrongCredentialsException;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.OfficeInfo;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;
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
    public Parcel addParcel(ParcelRequest parcelRequest) {
      

        Parcel parcel = parcelMapper.toEntity(parcelRequest,  null);

        if (parcelRequest.getOfficeId() != null) {
            Office office = officeRepository.findById(parcelRequest.getOfficeId())
                    .orElseThrow(() -> new EntityNotFound("Office not found"));
            parcel.setOfficeId(office.getId());
        } else {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                // Use the first office ID from user's office list
                String userOfficeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
                    ? user.getOfficeIds().get(0)
                    : null;
                if (userOfficeId != null) {
                    parcel.setOfficeId(userOfficeId);
                }
            }
        }
        if(parcelRequest.isHasCalled()) {
            parcel.setHasCalled(true);
        }
        
    if(parcelRequest.getShelfNumber() != null) {
        Shelf shelf = shelfRepository.findById(parcelRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));

        if( !shelf.getOffice().getId().equals(parcel.getOfficeId())) {
            throw new WrongCredentialsException("Shelf does not belong to the specified office");
        } 
        parcel.setShelfName(shelf.getName());
        parcel.setShelfId(shelf.getId());
    }
        
        if(parcelRequest.getTypeofParcel() == ParcelTypes.PICKUP && parcelRequest.getRiderId() != null) {
            User rider = userRepository.findById(parcelRequest.getRiderId())
                    .orElseThrow(() -> new EntityNotFound("Rider not found"));
            RiderInfo riderInfo = new RiderInfo();
            riderInfo.setRiderName(rider.getName());
            riderInfo.setRiderPhoneNumber(rider.getPhoneNumber());
            riderInfo.setRiderId(rider.getUserId());
            parcel.setRiderInfo(riderInfo);
        }

            if(parcelRequest.getTypeofParcel() == ParcelTypes.ONLINE) {
                if(parcelRequest.getFromOfficeId() != null) {
                    Office fromOffice = officeRepository.findById(parcelRequest.getFromOfficeId())
                            .orElseThrow(() -> new EntityNotFound("From office not found"));
                    OfficeInfo from = new OfficeInfo();
                    from.setOfficeId(fromOffice.getId());
                    from.setOfficeName(fromOffice.getName());
                    parcel.setFrom(from);
                }
    
                if(parcelRequest.getToOfficeId() != null) {
                    Office toOffice = officeRepository.findById(parcelRequest.getToOfficeId())
                            .orElseThrow(() -> new EntityNotFound("To office not found"));
                    OfficeInfo to = new OfficeInfo();
                    to.setOfficeId(toOffice.getId());
                    to.setOfficeName(toOffice.getName());
                    parcel.setOfficeId(parcelRequest.getToOfficeId());
                    parcel.setTo(to);
                }
            }
       
        Parcel savedParcel = parcelRepository.save(parcel);
        return savedParcel;
    }

    @Override
public Parcel updateParcel(String parcelId, ParcelUpdateRequest updateRequest) {

    Parcel parcel = parcelRepository.findById(parcelId)
            .orElseThrow(() -> new WrongCredentialsException("Parcel not found"));

    if (updateRequest.getDriverPhoneNumber() != null) {
        parcel.setDriverPhoneNumber(updateRequest.getDriverPhoneNumber());
    }

    if (updateRequest.getPickedUp()!= null) {
        parcel.setPickedUp(updateRequest.getPickedUp());
    }
    if (updateRequest.getDriverName() != null) {
        parcel.setDriverName(updateRequest.getDriverName());
    }

    if (updateRequest.getVehicleNumber() != null) {
        parcel.setVehicleNumber(updateRequest.getVehicleNumber());
    }

    if (updateRequest.getSenderPhoneNumber() != null) {
        parcel.setSenderPhoneNumber(updateRequest.getSenderPhoneNumber());
    }

    if (updateRequest.getSenderName() != null) {
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

    if (updateRequest.getIsPOD() != null) {
        parcel.setPOD(updateRequest.getIsPOD());
    }

    if (updateRequest.getIsDelivered() != null) {
        parcel.setDelivered(updateRequest.getIsDelivered());
    }

    if (updateRequest.getIsParcelAssigned() != null) {
        parcel.setParcelAssigned(updateRequest.getIsParcelAssigned());
    }

    if (updateRequest.getInboundCost() != null) {
        parcel.setInboundCost(updateRequest.getInboundCost());
    }

    if (updateRequest.getPickUpCost() != null) {
        parcel.setPickUpCost(updateRequest.getPickUpCost());
    }

    if (updateRequest.getIsFragile() != null) {
        parcel.setFragile(updateRequest.getIsFragile());
    }

    if (updateRequest.getDeliveryCost() != null) {
        parcel.setDeliveryCost(updateRequest.getDeliveryCost());
    }

    if (updateRequest.getStorageCost() != null) {
        parcel.setStorageCost(updateRequest.getStorageCost());
    }

    if (updateRequest.getHomeDelivery() != null) {
        parcel.setHomeDelivery(updateRequest.getHomeDelivery());
    }

    if (updateRequest.getHasCalled() != null) {
        parcel.setHasCalled(updateRequest.getHasCalled());
    }

    if (updateRequest.getShelfNumber() != null) {
        Shelf shelf = shelfRepository.findById(updateRequest.getShelfNumber())
                .orElseThrow(() -> new EntityNotFound("Shelf not found"));
        parcel.setShelfId(shelf.getId());
        parcel.setShelfName(shelf.getName());
    }

    // Payment and shelf info
    if (updateRequest.getPaymentMethod() != null) {
        parcel.setPaymentMethod(updateRequest.getPaymentMethod());
    }

    if (updateRequest.getShelfName() != null) {
        parcel.setShelfName(updateRequest.getShelfName());
    }

    if (updateRequest.getInboudPayed() != null) {
        parcel.setInboudPayed(updateRequest.getInboudPayed());
    }

    if (updateRequest.getShelfId() != null) {
        parcel.setShelfId(updateRequest.getShelfId());
    }

    // Parcel type
    if (updateRequest.getTypeofParcel() != null) {
        parcel.setTypeofParcel(updateRequest.getTypeofParcel());
    }

    // Online order fields
    if (updateRequest.getItemCost() != null) {
        parcel.setItemCost(updateRequest.getItemCost());
    }

    if (updateRequest.getIsItemOwnerPaid() != null) {
        parcel.setItemOwnerPaid(updateRequest.getIsItemOwnerPaid());
    }

    // Pickup fields
    if (updateRequest.getPickupAddress() != null) {
        parcel.setPickupAddress(updateRequest.getPickupAddress());
    }

    if (updateRequest.getPickupContactName() != null) {
        parcel.setPickupContactName(updateRequest.getPickupContactName());
    }

    if (updateRequest.getPickupContactPhoneNumber() != null) {
        parcel.setPickupContactPhoneNumber(updateRequest.getPickupContactPhoneNumber());
    }

    if (updateRequest.getPickupInstructions() != null) {
        parcel.setPickupInstructions(updateRequest.getPickupInstructions());
    }

    // Delivery fields
    if (updateRequest.getDeliveryAddress() != null) {
        parcel.setDeliveryAddress(updateRequest.getDeliveryAddress());
    }

    if (updateRequest.getDeliveryContactName() != null) {
        parcel.setDeliveryContactName(updateRequest.getDeliveryContactName());
    }

    if (updateRequest.getDeliveryContactPhoneNumber() != null) {
        parcel.setDeliveryContactPhoneNumber(updateRequest.getDeliveryContactPhoneNumber());
    }

    if (updateRequest.getSpecialInstructions() != null) {
        parcel.setSpecialInstructions(updateRequest.getSpecialInstructions());
    }

    return parcelRepository.save(parcel);
}


    @Override
    public Page<Parcel> searchParcels(
        Boolean isPOD,
        Boolean isDelivered,
        Boolean isParcelAssigned,
        String officeId,
        String driverPhoneNumber,
        Boolean hasCalled,
        Pageable pageable,
        boolean isFrontDesk) {

    if (isFrontDesk) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            // Get the first office ID from user's office list
            if (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty()) {
                officeId = user.getOfficeIds().get(0);
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

    // Apply criteria if any
    if (!criteria.isEmpty()) {
        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
    }

    // Apply default sorting BEFORE counting and pagination
    org.springframework.data.domain.Sort sort;
    if (pageable.getSort().isSorted()) {
        sort = pageable.getSort();
    } else {
        sort = org.springframework.data.domain.Sort.by(
            org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
    }
    query.with(sort);

    // Count total documents matching criteria
    long total = mongoTemplate.count(query, Parcel.class);

    // Apply pagination
    query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
    query.limit(pageable.getPageSize());

    // Execute query
    List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

    return new PageImpl<>(parcels, pageable, total);
}

    

    public UserResponse changeOffice(String officeId) {
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new EntityNotFound("Office not found"));

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Add the new office to the user's office list if not already present
        if (user.getOfficeIds() == null) {
            user.setOfficeIds(new java.util.ArrayList<>());
        }
        if (!user.getOfficeIds().contains(office.getId())) {
            user.getOfficeIds().add(office.getId());
        }
        userRepository.save(user);

        return new UserResponse("Office changed successfully", user.getPhoneNumber());
    }

    @Override
    public List<Parcel> getParcelsByDriverId(String driverId, boolean isPOD, String inboundPayed) {

        return null;

    }

    @Override
    //hasrole frontedesk, manager, admin
    @PreAuthorize("hasAnyRole('FRONTDESK', 'MANAGER')")    
    public Page<Parcel> getOnlineParcelsThatareMeantToBePayed(Pageable pageable) {
            Query query = new Query();
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof User user)) {
                throw new WrongCredentialsException("User not authenticated");
            }  
            List<Criteria> criteria = new ArrayList<>();

            criteria.add(Criteria.where("isItemOwnerPaid").is(false));
            criteria.add(Criteria.where("typeofParcel").is(ParcelTypes.ONLINE));
            criteria.add(Criteria.where("isDelivered").is(true));
            String officeId = user.getOfficeIds().get(0);
            criteria.add(Criteria.where("officeId").is(officeId));
            query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
            org.springframework.data.domain.Sort sort;
            if (pageable.getSort().isSorted()) {
                sort = pageable.getSort();
            } else {
                sort = org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
            }
            query.with(sort);

            // Count total documents matching criteria
            long total = mongoTemplate.count(query, Parcel.class);

            // Apply pagination
            query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
            query.limit(pageable.getPageSize());

            // Execute query
            List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

            return new PageImpl<>(parcels, pageable, total);
        };


    @Override
    public Page<Parcel> getHomeDeliveryParcels(Pageable pageable) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }


        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("homeDelivery").is(true));

        criteria.add(Criteria.where("isDelivered").is(false));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, Parcel.class);

        // Apply pagination
        //query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(300); //pageable.getPageSize());

        // Execute query
        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    public Page<Parcel> getUncalledParcels(Pageable pageable) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new WrongCredentialsException("User not authenticated");
        }

        // Get the first office ID from user's office list
        String officeId = (user.getOfficeIds() != null && !user.getOfficeIds().isEmpty())
            ? user.getOfficeIds().get(0)
            : null;

        if (officeId == null) {
            throw new WrongCredentialsException("User has no office assigned");
        }


        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("hasCalled").is(false));

        criteria.add(Criteria.where("officeId").is(officeId));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));

        org.springframework.data.domain.Sort sort;
        if (pageable.getSort().isSorted()) {
            sort = pageable.getSort();
        } else {
            sort = org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt");
        }
        query.with(sort);

        // Count total documents matching criteria
        long total = mongoTemplate.count(query, Parcel.class);

        // Apply pagination
        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        // Execute query
        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Page<Parcel> getYesterdayDeliveredParcelsNotCalledByCallCenter(Pageable pageable) {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        long startOfYesterday = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endOfYesterday = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        Query query = new Query();
        List<Criteria> criteria = new ArrayList<>();

        criteria.add(Criteria.where("isDelivered").is(true));
        //criteria.add(Criteria.where("hasCallCenterSpokenToClient").is(false));
        criteria.add(Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday));

        query.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

        long total = mongoTemplate.count(query, Parcel.class);

        query.skip((long) pageable.getPageNumber() * pageable.getPageSize());
        query.limit(pageable.getPageSize());

        List<Parcel> parcels = mongoTemplate.find(query, Parcel.class);

        return new PageImpl<>(parcels, pageable, total);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public Parcel updateCallCenterOutcome(String parcelId, CallCenterUpdateRequest request) {
        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found"));

        parcel.setCallOutCome(request.getCallOutCome());
        if (request.getCallOutCome() == CallCenterCallOutCome.REACHED) {
            parcel.setHasCallCenterSpokenToClient(true);
        }

        return parcelRepository.save(parcel);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public CallCenterStatsResponse getCallCenterStats() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        long startOfYesterday = yesterday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long endOfYesterday = yesterday.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1;

        Criteria deliveredYesterday = new Criteria().andOperator(
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday)
        );

        long totalDeliveredYesterday = mongoTemplate.count(new Query(deliveredYesterday), Parcel.class);

        long reached = mongoTemplate.count(new Query(new Criteria().andOperator(
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("callOutCome").is(CallCenterCallOutCome.REACHED)
        )), Parcel.class);

        long unreachable = mongoTemplate.count(new Query(new Criteria().andOperator(
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("callOutCome").is(CallCenterCallOutCome.UNREACHABLE)
        )), Parcel.class);

        long notCalled = mongoTemplate.count(new Query(new Criteria().andOperator(
                Criteria.where("isDelivered").is(true),
                Criteria.where("updatedAt").gte(startOfYesterday).lte(endOfYesterday),
                Criteria.where("hasCallCenterSpokenToClient").is(false),
                Criteria.where("callOutCome").isNull()
        )), Parcel.class);

        return CallCenterStatsResponse.builder()
                .totalDeliveredYesterday(totalDeliveredYesterday)
                .reached(reached)
                .unreachable(unreachable)
                .notCalled(notCalled)
                .build();
    }
}