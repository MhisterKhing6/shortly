package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.model.ParcelSystemLog;

@Repository
public interface ParcelSystemLogRepository extends MongoRepository<ParcelSystemLog, String> {
}
