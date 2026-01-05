package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.model.UserAction;

@Repository
public interface UserActionRepository extends MongoRepository<UserAction, String> {
}
