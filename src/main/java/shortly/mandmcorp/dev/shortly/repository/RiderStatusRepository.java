package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.model.RiderStatusModel;
import shortly.mandmcorp.dev.shortly.model.User;

@Repository
public interface RiderStatusRepository extends MongoRepository<RiderStatusModel, String> {
    RiderStatusModel findByRider(User rider);
}