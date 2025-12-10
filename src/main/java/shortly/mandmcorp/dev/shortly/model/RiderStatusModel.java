
package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

import org.springframework.data.mongodb.core.mapping.DBRef;
import shortly.mandmcorp.dev.shortly.enums.RiderStatus;

import shortly.mandmcorp.dev.shortly.model.User;

@Document(collection = "rider_status")
@Data
public class RiderStatusModel {
  @Id
  private String id;

  @DBRef
  private User rider;

  private RiderStatus riderStatus;
}