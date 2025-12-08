package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.Contacts;

public interface  ContaceRepository  extends  MongoRepository<Contacts, String>{
    public Contacts findByPhoneNumber(String phoneNumber); 

    public Contacts findByName(String name);
}
