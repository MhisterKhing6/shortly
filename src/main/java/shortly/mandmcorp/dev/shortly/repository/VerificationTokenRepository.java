package shortly.mandmcorp.dev.shortly.repository;



import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.VerificationToken;


public interface VerificationTokenRepository extends MongoRepository<VerificationToken, String> {
}
