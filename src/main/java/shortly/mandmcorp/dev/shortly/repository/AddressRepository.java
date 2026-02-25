package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.Address;

public interface  AddressRepository  extends  MongoRepository<Address, String>{
    List<Address> findAllByOfficeId(String officeId);

    List<Address> findAllByOfficeIdAndNameContainingIgnoreCase(String officeId,String name);
}
