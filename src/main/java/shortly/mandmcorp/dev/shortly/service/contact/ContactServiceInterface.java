package shortly.mandmcorp.dev.shortly.service.contact;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.model.Contacts;

public interface ContactServiceInterface {
    Page<Contacts> searchContacts(String name, String phoneNumber, String email, 
                                ContactType type, String vehicleNumber, String address, Pageable pageable);
}