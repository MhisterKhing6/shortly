package shortly.mandmcorp.dev.shortly.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import shortly.mandmcorp.dev.shortly.model.Contacts;
import shortly.mandmcorp.dev.shortly.model.Office;
import shortly.mandmcorp.dev.shortly.model.Parcel;

public interface ParcelRepository extends MongoRepository<Parcel, String> {
    Page<Parcel> findByIsPOD(boolean isPOD, Pageable pageable);
    Page<Parcel> findByIsDelivered(boolean isDelivered, Pageable pageable);
    Page<Parcel> findByIsParcelAssigned(boolean isParcelAssigned, Pageable pageable);
    Page<Parcel> findByOfficeId(Office office, Pageable pageable);
    Page<Parcel> findByDriver(Contacts driver, Pageable pageable);
    Page<Parcel> findByHasCalled(String hasCalled, Pageable pageable);
    
    @Query("{}")
    Page<Parcel> findAllParcels(Pageable pageable);
    
    java.util.List<Parcel> findByDriverIdAndIsPODAndInboudPayed(String driverId, boolean isPOD, String inboundPayed);
}