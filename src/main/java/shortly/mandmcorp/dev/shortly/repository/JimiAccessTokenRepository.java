package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.JimiAccessToken;

public interface JimiAccessTokenRepository extends MongoRepository<JimiAccessToken, String> {
}
