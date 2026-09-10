package shortly.mandmcorp.dev.shortly.repository;


import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import shortly.mandmcorp.dev.shortly.model.DriverReconcilation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface DriverReconcilationRepository extends MongoRepository<DriverReconcilation, String> {

    Optional<DriverReconcilation> findByIdAndPayedFalse(String id);

    List<DriverReconcilation> findByPayedFalse();

    Page<DriverReconcilation> findByPayedFalse(Pageable pageable);

    Page<DriverReconcilation> findByOfficeIdAndPayedFalse(String officeId, Pageable pageable);

    Page<DriverReconcilation> findByOfficeIdAndCompanyIdAndPayedFalse(String officeId, String companyId, Pageable pageable);
}