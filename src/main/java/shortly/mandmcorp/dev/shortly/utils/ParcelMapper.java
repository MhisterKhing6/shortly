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
        parcel.setSender(sender);
        parcel.setReceiver(receiver);
        parcel.setParcelDescription(request.getParcelDescription());
        parcel.setPOD(request.isPOD());
        parcel.setDelivered(request.isDelivered());
        parcel.setParcelAssigned(request.isParcelAssigned());
        parcel.setInboundCost(request.getInboundCost());
        parcel.setPickUpCost(request.getPickUpCost());
        parcel.setFragile(request.isFragile());
        parcel.setDeliveryCost(request.getDeliveryCost());
        parcel.setStorageCost(request.getStorageCost());
        parcel.setShelf(shelf);
        parcel.setDriver(driver);
        return parcel;
    }
    
    public ParcelResponse toResponse(Parcel parcel, Contacts driver, Contacts sender, Contacts receiver) {
        ParcelResponse response = new ParcelResponse();
        response.setParcelId(parcel.getParcelId());
        response.setSenderName(sender.getName());
        response.setSenderPhoneNumber(sender.getPhoneNumber());
        response.setReceiverName(receiver.getName());
        response.setReceiverAddress(receiver.getAddress());
        response.setRecieverPhoneNumber(receiver.getPhoneNumber());
        response.setDriverName(driver.getName());
        response.setDriverPhoneNumber(driver.getPhoneNumber());
        response.setParcelDescription(parcel.getParcelDescription());
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
    
    public Contacts getOrCreateSender(String phoneNumber, String name) {
        Contacts sender = contactRepository.findByPhoneNumber(phoneNumber);
        if(sender == null) {
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
        if(receiver == null) {
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
        if(driver == null) {
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