package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import shortly.mandmcorp.dev.shortly.model.Parcel;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
    Page<Parcel> findByIsPOD(boolean isPOD, Pageable pageable);

Page<Parcel> findByIsDelivered(boolean isDelivered, Pageable pageable);

Page<Parcel> findByIsParcelAssigned(boolean isParcelAssigned, Pageable pageable);

Page<Parcel> findByOfficeId(String officeId, Pageable pageable);

Page<Parcel> findByDriverPhoneNumber(String driverPhoneNumber, Pageable pageable);

Page<Parcel> findByHasCalled(boolean hasCalled, Pageable pageable);

@Query("{}")
Page<Parcel> findAll(Pageable pageable);

List<Parcel> findByDriverPhoneNumberAndIsPOD(
        String driverPhoneNumber,
        boolean isPOD,
        boolean inboundPaid
);

}