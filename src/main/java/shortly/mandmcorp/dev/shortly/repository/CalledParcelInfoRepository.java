package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.CalledParcelInfo;

public interface CalledParcelInfoRepository extends  MongoRepository<CalledParcelInfo, String> {
    
}
