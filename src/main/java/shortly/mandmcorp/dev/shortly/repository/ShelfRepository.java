package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Shelf;
import java.util.List;


public interface  ShelfRepository   extends MongoRepository<Shelf, String>{ 
    Shelf findByNameAndOffice(String name, Office office);

    List<Shelf> findByOffice(Office office);
}
