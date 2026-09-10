
package shortly.mandmcorp.dev.shortly.repository;


import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.enums.UserRole;
import shortly.mandmcorp.dev.shortly.model.User;

public interface  UserRepository extends  MongoRepository  <User,String> {
    User findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    User findByPhoneNumber(String phoneNumber);
    User findByUserId(String userId);
    List<User> findByRoleAndOfficeIdsContainingAndAvailability(UserRole role, String officeId, boolean availability);
    List<User> findByRoleAndOfficeIdsContaining(UserRole role, String officeId);
    Page<User> findByOfficeIdsContaining(String officeId, Pageable pageable);
    Page<User> findByCompanyId(String companyId, Pageable pageable);
    Page<User> findByCompanyIdAndOfficeIdsContaining(String companyId, String officeId, Pageable pageable);

}