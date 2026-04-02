package shortly.mandmcorp.dev.shortly.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.DriverAssignment;

public interface DriverAssignmentRepository extends  MongoRepository<DriverAssignment, String> {
    
    Optional<DriverAssignment> findByParcelId(String parcelId);
}
