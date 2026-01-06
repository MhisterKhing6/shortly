package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.CancelationReason;

public interface  CancelationReasonRepository extends MongoRepository<CancelationReason, String> {
    CancelationReason findByReason(String reason);

}
