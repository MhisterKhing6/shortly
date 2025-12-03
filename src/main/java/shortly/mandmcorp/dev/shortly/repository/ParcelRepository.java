package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import shortly.mandmcorp.dev.shortly.model.Parcel;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
}