package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;

@Repository
public interface DeliveryAssignmentsRepository extends MongoRepository<DeliveryAssignments, String> {
    // Updated to use embedded riderInfo
    @Query("{'riderInfo.riderId': ?0}")
    List<DeliveryAssignments> findByRiderInfoRiderId(String riderId);

    @Query("{'riderInfo.riderId': ?0, 'status': {$ne: ?1}}")
    List<DeliveryAssignments> findByRiderInfoRiderIdAndStatusNot(String riderId, DeliveryStatus status);

    @Query("{'riderInfo.riderId': ?0, 'parcels.receiverPhoneNumber': ?1, 'status': {$ne: 'DELIVERED'}}")
    List<DeliveryAssignments> findByRiderAndReceiverPhoneAndNotDelivered(String riderId, String receiverPhone);

    @Query("{'riderInfo.riderId': ?0, 'payed': ?1}")
    List<DeliveryAssignments> findByRiderIdAndPayed(String riderId, boolean payed);

    List<DeliveryAssignments> findByStatusAndOfficeId(DeliveryStatus status, String officeId);
    List<DeliveryAssignments> findByStatus(DeliveryStatus status);

    List<DeliveryAssignments> findByPayedAndOfficeId(boolean payed, String officeId);

}