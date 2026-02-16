package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import shortly.mandmcorp.dev.shortly.repository.ContaceRepository;
import shortly.mandmcorp.dev.shortly.repository.OfficeRepository;

@Component
@AllArgsConstructor
public class ParcelMapper {

    private final ContaceRepository contactRepository;
    private final OfficeRepository officeRepository;

    public Parcel toEntity(ParcelRequest request, Shelf shelf) {
        Parcel parcel = new Parcel();
        parcel.setSenderName(request.getSenderName());
        parcel.setSenderPhoneNumber(request.getSenderPhoneNumber());
        parcel.setReceiverName(request.getReceiverName());
        parcel.setReceiverAddress(request.getReceiverAddress());
        parcel.setRecieverPhoneNumber(request.getRecieverPhoneNumber());
        parcel.setParcelDescription(request.getParcelDescription());
        parcel.setPOD(request.isPOD());
        parcel.setDelivered(request.isDelivered());
        parcel.setParcelAssigned(request.isParcelAssigned());
        parcel.setInboundCost(request.getInboundCost());
        parcel.setPickUpCost(request.getPickUpCost());
        parcel.setFragile(request.isFragile());
        parcel.setDeliveryCost(request.getDeliveryCost());
        parcel.setStorageCost(request.getStorageCost());
        parcel.setVehicleNumber(request.getVehicleNumber());
        parcel.setDriverName(request.getDriverName());
        parcel.setDriverPhoneNumber(request.getDriverPhoneNumber());
        parcel.setHomeDelivery(request.isHomeDelivery());
        parcel.setHasCalled(request.isHasCalled());
        parcel.setPickedUp(request.isPickedUp());

        // Payment and shelf info
        parcel.setPaymentMethod(request.getPaymentMethod());
        parcel.setShelfName(request.getShelfName());
        parcel.setInboudPayed(request.isInboudPayed());
        parcel.setShelfId(request.getShelfId());

        // Parcel type
        parcel.setTypeofParcel(request.getTypeofParcel());

        // Online order fields
        parcel.setItemCost(request.getItemCost());
        parcel.setItemOwnerPaid(request.isItemOwnerPaid());

        // Pickup fields
        parcel.setPickupAddress(request.getPickupAddress());
        parcel.setPickupContactName(request.getPickupContactName());
        parcel.setPickupContactPhoneNumber(request.getPickupContactPhoneNumber());
        parcel.setPickupInstructions(request.getPickupInstructions());

        // Delivery fields
        parcel.setDeliveryAddress(request.getDeliveryAddress());
        parcel.setDeliveryContactName(request.getDeliveryContactName());
        parcel.setDeliveryContactPhoneNumber(request.getDeliveryContactPhoneNumber());
        parcel.setSpecialInstructions(request.getSpecialInstructions());

        return parcel;
    }

    /**
     * Fully null-safe mapping from Parcel → ParcelResponse
     */
    
    public Contacts getOrCreateSender(String phoneNumber, String name) {
        Contacts sender = contactRepository.findByPhoneNumber(phoneNumber);
        if (sender == null) {
            sender = Contacts.builder()
                    .phoneNumber(phoneNumber)
                    .name(name)
                    .type(ContactType.SENDER)
                    .build();
            contactRepository.save(sender);
        }
        return sender;
    }

    public Contacts getOrCreateReceiver(String phoneNumber, String name, String address) {
        Contacts receiver = contactRepository.findByPhoneNumber(phoneNumber);
        if (receiver == null) {
            receiver = Contacts.builder()
                    .phoneNumber(phoneNumber)
                    .name(name)
                    .address(address)
                    .type(ContactType.RECEIVER)
                    .build();
            contactRepository.save(receiver);
        }
        return receiver;
    }

    public Contacts getOrCreateDriver(String phoneNumber, String name, String vehicleNumber) {
        Contacts driver = contactRepository.findByPhoneNumber(phoneNumber);
        if (driver == null) {
            driver = Contacts.builder()
                    .phoneNumber(phoneNumber)
                    .name(name)
                    .vehicleNumber(vehicleNumber)
                    .type(ContactType.DRIVER)
                    .build();
            contactRepository.save(driver);
        }
        return driver;
    }

    public Office getOfficeById(String officeId) {
        return officeRepository.findById(officeId).orElse(null);
    }
}