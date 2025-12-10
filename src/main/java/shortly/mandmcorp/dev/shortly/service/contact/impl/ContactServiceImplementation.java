package shortly.mandmcorp.dev.shortly.service.contact.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.repository.ContaceRepository;
import shortly.mandmcorp.dev.shortly.service.contact.ContactServiceInterface;

/**
 * Service implementation for contact management operations.
 * Handles contact search and filtering with pagination.
 * 
 * @author Shortly Team
 * @version 1.0
 * @since 1.0
 */
@Service
@Slf4j
@AllArgsConstructor
public class ContactServiceImplementation implements ContactServiceInterface {
    
    private final ContaceRepository contactRepository;
    
    /**
     * Searches contacts with various filters and pagination.
     * 
     * @param name contact name filter
     * @param phoneNumber phone number filter
     * @param email email filter
     * @param type contact type filter
     * @param vehicleNumber vehicle number filter
     * @param address address filter
     * @param pageable pagination parameters
     * @return Page of ContactResponse
     */
    @Override
    public Page<Contacts> searchContacts(String name, String phoneNumber, String email, 
                                       ContactType type, String vehicleNumber, String address, Pageable pageable) {
        log.info("Searching contacts with filters - name: {}, phone: {}, email: {}, type: {}, vehicle: {}, address: {}", 
                name, phoneNumber, email, type, vehicleNumber, address);
        
        Page<Contacts> contacts;
        
        if(name != null && !name.isEmpty()) {
            contacts = contactRepository.findByNameContainingIgnoreCase(name, pageable);
        } else if(phoneNumber != null && !phoneNumber.isEmpty()) {
            contacts = contactRepository.findByPhoneNumberContaining(phoneNumber, pageable);
        } else if(email != null && !email.isEmpty()) {
            contacts = contactRepository.findByEmailContainingIgnoreCase(email, pageable);
        } else if(type != null) {
            contacts = contactRepository.findByType(type, pageable);
        } else if(vehicleNumber != null && !vehicleNumber.isEmpty()) {
            contacts = contactRepository.findByVehicleNumberContainingIgnoreCase(vehicleNumber, pageable);
        } else if(address != null && !address.isEmpty()) {
            contacts = contactRepository.findByAddressContainingIgnoreCase(address, pageable);
        } else {
            contacts = contactRepository.findAllContacts(pageable);
        }
        
        return contacts;
    }
}