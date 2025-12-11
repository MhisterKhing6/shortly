package shortly.mandmcorp.dev.shortly.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import shortly.mandmcorp.dev.shortly.enums.DeliveryStatus;
import shortly.mandmcorp.dev.shortly.model.DeliveryAssignments;
import shortly.mandmcorp.dev.shortly.model.User;

@Repository
public interface DeliveryAssignmentsRepository extends MongoRepository<DeliveryAssignments, String> {
    List<DeliveryAssignments> findByRiderId(User rider);
    List<DeliveryAssignments> findByRiderIdAndStatusNot(User rider, DeliveryStatus status);
    
    @Query("{'riderId': ?0, 'orderId.receiver.phoneNumber': ?1, 'status': {$ne: 'DELIVERED'}}")
    List<DeliveryAssignments> findByRiderAndReceiverPhoneAndNotDelivered(User rider, String receiverPhone);
    
    List<DeliveryAssignments> findByRiderIdUserIdAndPayed(String riderId, boolean payed);
}