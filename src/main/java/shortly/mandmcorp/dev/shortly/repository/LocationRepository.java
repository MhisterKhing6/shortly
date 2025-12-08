package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import shortly.mandmcorp.dev.shortly.model.Location;

public interface LocationRepository extends MongoRepository<Location, String> {
    Location findByName(String name);
    List<Location> findByNameContainingIgnoreCase(String name);
}