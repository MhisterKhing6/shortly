package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.enums.ContactType;
import shortly.mandmcorp.dev.shortly.model.Contacts;

@Repository
public interface ContaceRepository extends MongoRepository<Contacts, String> {
    Contacts findByPhoneNumber(String phoneNumber);
    Contacts findByName(String name);
    
    Page<Contacts> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Contacts> findByPhoneNumberContaining(String phoneNumber, Pageable pageable);
    Page<Contacts> findByEmailContainingIgnoreCase(String email, Pageable pageable);
    Page<Contacts> findByType(ContactType type, Pageable pageable);
    Page<Contacts> findByVehicleNumberContainingIgnoreCase(String vehicleNumber, Pageable pageable);
    Page<Contacts> findByAddressContainingIgnoreCase(String address, Pageable pageable);
    
    @Query("{}")
    Page<Contacts> findAllContacts(Pageable pageable);
}
