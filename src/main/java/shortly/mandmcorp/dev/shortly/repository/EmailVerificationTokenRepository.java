package shortly.mandmcorp.dev.shortly.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.EmailVerificationToken;

public interface EmailVerificationTokenRepository extends MongoRepository<EmailVerificationToken, String> {
    Optional<EmailVerificationToken> findByToken(String token);
    Optional<EmailVerificationToken> findFirstByCompanyIdOrderByCreatedAtDesc(String companyId);
}
