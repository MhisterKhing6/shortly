
package shortly.mandmcorp.dev.shortly.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;
import shortly.mandmcorp.dev.shortly.enums.RiderStatus;

@Document(collection = "rider_status")
@Data
public class RiderStatusModel {
  @Id
  private String id;

  @DBRef
  private User rider;

  private RiderStatus riderStatus;
  
  @CreatedDate
  private LocalDateTime createdAt;
  
  @LastModifiedDate
  private LocalDateTime updatedAt;
}