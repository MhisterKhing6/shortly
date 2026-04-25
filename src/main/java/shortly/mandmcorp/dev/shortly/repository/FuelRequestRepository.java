package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import shortly.mandmcorp.dev.shortly.enums.FuelRequestStatus;
import shortly.mandmcorp.dev.shortly.model.FuelRequest;

public interface FuelRequestRepository extends MongoRepository<FuelRequest, String> {
    long countByStatus(FuelRequestStatus status);
    Page<FuelRequest> findByStatus(FuelRequestStatus status, Pageable pageable);
    Page<FuelRequest> findByRiderInfo_RiderId(String riderId, Pageable pageable);
}
