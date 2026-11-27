package shortly.mandmcorp.dev.shortly.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import shortly.mandmcorp.dev.shortly.model.Company;

public interface CompanyRepository extends MongoRepository<Company, String> {
    Optional<Company> findByCompanyNameIgnoreCase(String companyName);
    Optional<Company> findByEmailIgnoreCase(String email);
    boolean existsByCompanyNameIgnoreCase(String companyName);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}
