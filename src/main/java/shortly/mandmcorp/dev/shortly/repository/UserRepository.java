
package shortly.mandmcorp.dev.shortly.repository;


import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.User;

public interface  UserRepository extends  MongoRepository  <User,String> {
    User findByEmail(String email);
    User findByPhoneNumber(String phoneNumber);
    User findByUserId(String userId);
    List<User> findByRoleAndOfficeIdsContainingAndAvailability(UserRole role, String officeId, boolean availability);
    List<User> findByRoleAndOfficeIdsContaining(UserRole role, String officeId);

}