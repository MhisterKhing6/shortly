package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.Location;
import shortly.mandmcorp.dev.shortly.model.Office;

public interface OfficeRepository extends MongoRepository<Office, String>{
   Office findByName(String officeName);
   Optional<Office> findByCode(String officeCode);
   List<Office> findByLocation(Location location);
   List<Office> findByNameContainingIgnoreCase(String name);
   Optional<Office> findFirstByNameAndLocation(String name, Location location);
}
