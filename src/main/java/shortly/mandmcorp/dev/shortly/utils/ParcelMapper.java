package shortly.mandmcorp.dev.shortly.utils;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import shortly.mandmcorp.dev.shortly.dto.request.ParcelRequest;
import shortly.mandmcorp.dev.shortly.dto.response.ParcelResponse;
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

    public Parcel toEntity(ParcelRequest request, Contacts driver, Contacts sender, Contacts receiver, Shelf shelf) {
        Parcel parcel = new Parcel();
        parcel.setSenderName(sender.getName());
        parcel.setSenderPhoneNumber(sender.getPhoneNumber());
        parcel.setReceiverName(receiver.getName());
        parcel.setReceiverAddress(receiver.getAddress());
        parcel.setRecieverPhoneNumber(receiver.getPhoneNumber());
        parcel.setParcelDescription(request.getParcelDescription());
        parcel.setPOD(request.isPOD());
        parcel.setDelivered(request.isDelivered());
        parcel.setParcelAssigned(request.isParcelAssigned());
        parcel.setInboundCost(request.getInboundCost());
        parcel.setPickUpCost(request.getPickUpCost());
        parcel.setFragile(request.isFragile());
        parcel.setDeliveryCost(request.getDeliveryCost());
        parcel.setStorageCost(request.getStorageCost());
        parcel.setVehicleNumber(driver.getVehicleNumber());
        parcel.setDriverName(driver.getName());
        parcel.setDriverPhoneNumber(driver.getPhoneNumber());
        parcel.setHomeDelivery(request.isHomeDelivery());
        return parcel;
    }

    /**
     * Fully null-safe mapping from Parcel → ParcelResponse
     */
    public ParcelResponse toResponse(Parcel parcel, Contacts driver, Contacts sender, Contacts receiver) {
        ParcelResponse response = new ParcelResponse();

        response.setParcelId(parcel.getParcelId());
        response.setParcelDescription(parcel.getParcelDescription());
        response.setPOD(parcel.isPOD());
        response.setDelivered(parcel.isDelivered());
        response.setParcelAssigned(parcel.isParcelAssigned());
        response.setInboundCost(parcel.getInboundCost());
        response.setPickUpCost(parcel.getPickUpCost());
        response.setFragile(parcel.isFragile());
        response.setDeliveryCost(parcel.getDeliveryCost());
        response.setStorageCost(parcel.getStorageCost());

        // ---------- SENDER (null-safe) ----------
        if (sender != null) {
            response.setSenderName(sender.getName());
            response.setSenderPhoneNumber(sender.getPhoneNumber());
        } else {
            response.setSenderName(null);
            response.setSenderPhoneNumber(null);
        }

        // ---------- RECEIVER (null-safe) — THIS WAS CAUSING THE NPE ----------
        if (receiver != null) {
            response.setReceiverName(receiver.getName());
            response.setReceiverAddress(receiver.getAddress());
            response.setRecieverPhoneNumber(receiver.getPhoneNumber());
        } else {
            response.setReceiverName(null);
            response.setReceiverAddress(null);
            response.setRecieverPhoneNumber(null);
        }

        // ---------- DRIVER (null-safe) ----------
        if (driver != null) {
            response.setDriverName(driver.getName());
            response.setDriverPhoneNumber(driver.getPhoneNumber());
            // vehicleNumber is driver-specific
            response.setVehicleNumber(driver.getVehicleNumber());
        } else {
            response.setDriverName(null);
            response.setDriverPhoneNumber(null);
            response.setVehicleNumber(null);
        }

        return response;
    }

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