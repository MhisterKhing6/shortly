package shortly.mandmcorp.dev.shortly.service.tracking.impl;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.dto.request.TrackingAssignDriverRequest;
import shortly.mandmcorp.dev.shortly.dto.request.TrackingStatusUpdateRequest;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.AdminTrackingResponse;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.CustomerTrackingResponse;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.FrontdeskTrackingResponse;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.PublicTrackingResponse;
import shortly.mandmcorp.dev.shortly.dto.response.tracking.RiderTrackingResponse;
import shortly.mandmcorp.dev.shortly.enums.ParcelStatus;
import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.exceptions.EntityNotFound;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.RiderInfo;
import shortly.mandmcorp.dev.shortly.model.User;
import shortly.mandmcorp.dev.shortly.repository.ParcelRepository;
import shortly.mandmcorp.dev.shortly.repository.UserRepository;
import shortly.mandmcorp.dev.shortly.service.tracking.ParcelTrackingServiceInterface;

@Service
@AllArgsConstructor
@Slf4j
public class ParcelTrackingServiceImplementation implements ParcelTrackingServiceInterface {

    private final ParcelRepository parcelRepository;
    private final UserRepository userRepository;

    @Override
    public PublicTrackingResponse getPublicTracking(String parcelId) {
        Parcel parcel = findParcelOrThrow(parcelId);
        return mapToPublicResponse(parcel);
    }

    @Override
    public Object getRoleAwareTracking(String parcelId) {
        Parcel parcel = findParcelOrThrow(parcelId);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User user = (User) auth.getPrincipal();
        UserRole role = user.getRole();

        return switch (role) {
            case CUSTOMER, VENDOR -> mapToCustomerResponse(parcel);
            case RIDER -> mapToRiderResponse(parcel);
            case FRONTDESK -> mapToFrontdeskResponse(parcel);
            case ADMIN, MANAGER, CALLCENTER -> mapToAdminResponse(parcel);
        };
    }

    @Override
    @PreAuthorize("hasAnyRole('RIDER', 'ADMIN', 'MANAGER')")
    public Parcel updateTrackingStatus(String parcelId, TrackingStatusUpdateRequest request) {
        Parcel parcel = findParcelOrThrow(parcelId);
        parcel.setParcelStatus(request.getParcelStatus());

        if (request.getParcelStatus() == ParcelStatus.DELIVERD) {
            parcel.setDelivered(true);
        }

        if (request.getNotes() != null) {
            parcel.setNotes(request.getNotes());
        }

        return parcelRepository.save(parcel);
    }

    @Override
    @PreAuthorize("hasAnyRole('FRONTDESK', 'ADMIN', 'MANAGER')")
    public Parcel assignDriverToParcel(String parcelId, TrackingAssignDriverRequest request) {
        Parcel parcel = findParcelOrThrow(parcelId);
        User rider = userRepository.findByUserId(request.getRiderId());
        if (rider == null) {
            throw new EntityNotFound("Rider not found with ID: " + request.getRiderId());
        }

        parcel.setRiderId(rider.getUserId());
        parcel.setRiderInfo(RiderInfo.builder()
                .riderId(rider.getUserId())
                .riderName(rider.getName())
                .riderPhoneNumber(rider.getPhoneNumber())
                .build());
        parcel.setParcelAssigned(true);

        return parcelRepository.save(parcel);
    }

    private Parcel findParcelOrThrow(String parcelId) {
        return parcelRepository.findById(parcelId)
                .orElseThrow(() -> new EntityNotFound("Parcel not found with ID: " + parcelId));
    }

    private PublicTrackingResponse mapToPublicResponse(Parcel parcel) {
        return PublicTrackingResponse.builder()
                .parcelId(parcel.getParcelId())
                .parcelStatus(parcel.getParcelStatus())
                .typeofParcel(parcel.getTypeofParcel())
                .isDelivered(parcel.isDelivered())
                .isParcelAssigned(parcel.isParcelAssigned())
                .fromOfficeName(parcel.getFrom() != null ? parcel.getFrom().getOfficeName() : null)
                .toOfficeName(parcel.getTo() != null ? parcel.getTo().getOfficeName() : null)
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }

    private CustomerTrackingResponse mapToCustomerResponse(Parcel parcel) {
        return CustomerTrackingResponse.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .parcelStatus(parcel.getParcelStatus())
                .typeofParcel(parcel.getTypeofParcel())
                .isDelivered(parcel.isDelivered())
                .isParcelAssigned(parcel.isParcelAssigned())
                .senderName(maskName(parcel.getSenderName()))
                .receiverName(parcel.getReceiverName())
                .fromOfficeName(parcel.getFrom() != null ? parcel.getFrom().getOfficeName() : null)
                .toOfficeName(parcel.getTo() != null ? parcel.getTo().getOfficeName() : null)
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .timeline(buildTimeline(parcel))
                .build();
    }

    private RiderTrackingResponse mapToRiderResponse(Parcel parcel) {
        return RiderTrackingResponse.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .parcelStatus(parcel.getParcelStatus())
                .typeofParcel(parcel.getTypeofParcel())
                .isDelivered(parcel.isDelivered())
                .isPOD(parcel.isPOD())
                .isFragile(parcel.isFragile())
                .homeDelivery(parcel.isHomeDelivery())
                .receiverName(parcel.getReceiverName())
                .receiverAddress(parcel.getReceiverAddress())
                .recieverPhoneNumber(parcel.getRecieverPhoneNumber())
                .alternativePhoneNumber(parcel.getAlternativePhoneNumber())
                .pickupAddress(parcel.getPickupAddress())
                .pickupContactName(parcel.getPickupContactName())
                .pickupContactPhoneNumber(parcel.getPickupContactPhoneNumber())
                .pickupInstructions(parcel.getPickupInstructions())
                .deliveryAddress(parcel.getDeliveryAddress())
                .deliveryContactName(parcel.getDeliveryContactName())
                .deliveryContactPhoneNumber(parcel.getDeliveryContactPhoneNumber())
                .specialInstructions(parcel.getSpecialInstructions())
                .deliveryCost(parcel.getDeliveryCost())
                .pickUpCost(parcel.getPickUpCost())
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }

    private FrontdeskTrackingResponse mapToFrontdeskResponse(Parcel parcel) {
        return FrontdeskTrackingResponse.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .parcelStatus(parcel.getParcelStatus())
                .typeofParcel(parcel.getTypeofParcel())
                .isDelivered(parcel.isDelivered())
                .isParcelAssigned(parcel.isParcelAssigned())
                .isPOD(parcel.isPOD())
                .isFragile(parcel.isFragile())
                .homeDelivery(parcel.isHomeDelivery())
                .senderName(parcel.getSenderName())
                .senderPhoneNumber(parcel.getSenderPhoneNumber())
                .receiverName(parcel.getReceiverName())
                .receiverAddress(parcel.getReceiverAddress())
                .recieverPhoneNumber(parcel.getRecieverPhoneNumber())
                .alternativePhoneNumber(parcel.getAlternativePhoneNumber())
                .shelfName(parcel.getShelfName())
                .shelfId(parcel.getShelfId())
                .driverName(parcel.getDriverName())
                .driverPhoneNumber(parcel.getDriverPhoneNumber())
                .driverId(parcel.getDriverId())
                .officeId(parcel.getOfficeId())
                .deliveryCost(parcel.getDeliveryCost())
                .inboundCost(parcel.getInboundCost())
                .storageCost(parcel.getStorageCost())
                .paymentMethod(parcel.getPaymentMethod())
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }

    private AdminTrackingResponse mapToAdminResponse(Parcel parcel) {
        return AdminTrackingResponse.builder()
                .parcelId(parcel.getParcelId())
                .parcelDescription(parcel.getParcelDescription())
                .parcelStatus(parcel.getParcelStatus())
                .typeofParcel(parcel.getTypeofParcel())
                .isPOD(parcel.isPOD())
                .isDelivered(parcel.isDelivered())
                .isParcelAssigned(parcel.isParcelAssigned())
                .isFragile(parcel.isFragile())
                .homeDelivery(parcel.isHomeDelivery())
                .pickedUp(parcel.isPickedUp())
                .inboundCost(parcel.getInboundCost())
                .deliveryCost(parcel.getDeliveryCost())
                .storageCost(parcel.getStorageCost())
                .pickUpCost(parcel.getPickUpCost())
                .ItemCost(parcel.getItemCost())
                .paymentMethod(parcel.getPaymentMethod())
                .senderName(parcel.getSenderName())
                .senderPhoneNumber(parcel.getSenderPhoneNumber())
                .receiverName(parcel.getReceiverName())
                .receiverAddress(parcel.getReceiverAddress())
                .recieverPhoneNumber(parcel.getRecieverPhoneNumber())
                .alternativePhoneNumber(parcel.getAlternativePhoneNumber())
                .driverId(parcel.getDriverId())
                .driverName(parcel.getDriverName())
                .driverPhoneNumber(parcel.getDriverPhoneNumber())
                .vehicleNumber(parcel.getVehicleNumber())
                .officeId(parcel.getOfficeId())
                .shelfName(parcel.getShelfName())
                .shelfId(parcel.getShelfId())
                .inboudPayed(parcel.isInboudPayed())
                .riderId(parcel.getRiderId())
                .riderInfo(parcel.getRiderInfo())
                .from(parcel.getFrom())
                .to(parcel.getTo())
                .fromOfficeId(parcel.getFromOfficeId())
                .toOfficeId(parcel.getToOfficeId())
                .hasArrivedAtOffice(parcel.isHasArrivedAtOffice())
                .isItemOwnerPaid(parcel.isItemOwnerPaid())
                .parcelTransfer(parcel.isParcelTransfer())
                .pickupAddress(parcel.getPickupAddress())
                .pickupContactName(parcel.getPickupContactName())
                .pickupContactPhoneNumber(parcel.getPickupContactPhoneNumber())
                .pickupInstructions(parcel.getPickupInstructions())
                .deliveryAddress(parcel.getDeliveryAddress())
                .deliveryContactName(parcel.getDeliveryContactName())
                .deliveryContactPhoneNumber(parcel.getDeliveryContactPhoneNumber())
                .specialInstructions(parcel.getSpecialInstructions())
                .vendorName(parcel.getVendorName())
                .vendorId(parcel.getVendorId())
                .vendorDeliveryFee(parcel.getVendorDeliveryFee())
                .numberOfItems(parcel.getNumberOfItems())
                .itemQuantity(parcel.getItemQuantity())
                .parcelWeight(parcel.getParcelWeight())
                .isVendorPayed(parcel.isVendorPayed())
                .callerName(parcel.getCallerName())
                .callerPhoneNumber(parcel.getCallerPhoneNumber())
                .notes(parcel.getNotes())
                .hasCallCenterSpokenToClient(parcel.isHasCallCenterSpokenToClient())
                .callOutCome(parcel.getCallOutCome())
                .callCenterRemark(parcel.getCallCenterRemark())
                .hasCalled(parcel.isHasCalled())
                .returnCount(parcel.getReturnCount())
                .imageUrls(parcel.getImageUrls())
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }

    private String maskName(String name) {
        if (name == null || name.length() <= 2) {
            return name;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1);
    }

    private List<CustomerTrackingResponse.TrackingEvent> buildTimeline(Parcel parcel) {
        List<CustomerTrackingResponse.TrackingEvent> events = new ArrayList<>();

        if (parcel.getCreatedAt() != null) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("RECEIVED")
                    .description("Parcel received")
                    .timestamp(parcel.getCreatedAt())
                    .build());
        }

        if (parcel.isParcelAssigned()) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("ASSIGNED")
                    .description("Assigned for delivery")
                    .timestamp(parcel.getUpdatedAt())
                    .build());
        }

        if (parcel.isPickedUp()) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("PICKED_UP")
                    .description("Picked up by rider")
                    .timestamp(parcel.getUpdatedAt())
                    .build());
        }

        if (parcel.getParcelStatus() == ParcelStatus.DELIVERD || parcel.isDelivered()) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("DELIVERED")
                    .description("Parcel delivered")
                    .timestamp(parcel.getUpdatedAt())
                    .build());
        }

        if (parcel.getParcelStatus() == ParcelStatus.FAILED) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("FAILED")
                    .description("Delivery failed")
                    .timestamp(parcel.getUpdatedAt())
                    .build());
        }

        if (parcel.getParcelStatus() == ParcelStatus.REVERSED) {
            events.add(CustomerTrackingResponse.TrackingEvent.builder()
                    .status("REVERSED")
                    .description("Parcel returned")
                    .timestamp(parcel.getUpdatedAt())
                    .build());
        }

        return events;
    }
}
