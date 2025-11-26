
package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.User;

public interface  UserRepository extends  MongoRepository  <User,String> {
    User findByEmail(String email);
    User findByPhoneNumber(String phoneNumber);
    User findByUserId(String userId);
}