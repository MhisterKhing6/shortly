package shortly.mandmcorp.dev.shortly.repository;
import org.springframework.data.mongodb.repository.MongoRepository;
import shortly.mandmcorp.dev.shortly.model.Reconcilations;
import java.util.Optional;

public interface ReconcilationRepository extends  MongoRepository<Reconcilations, String> {

    Optional<Reconcilations> findByAssignmentId(String assignmentId);

}
