package shortly.mandmcorp.dev.shortly.repository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import java.util.List;
import java.util.Optional;

public interface ReconcilationRepository extends  MongoRepository<Reconcilations, String> {

    Optional<Reconcilations> findByAssignmentId(String assignmentId);

    @Query("{'riderId': ?0}")
    List<Reconcilations> findByRiderId(String riderId, Sort sort);

}
