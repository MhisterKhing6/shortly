package shortly.mandmcorp.dev.shortly.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AddOfficeToUserRequest {
   @NotBlank(message="Office ID cannot be blank") 
   private String officeId;

   @NotBlank(message="User phone number cannot be blank")
   @Pattern(regexp = "^(\\+233|0)[2-9][0-9]{8}$", message = "Phone number must be a valid Ghana phone number")
   private String userPhoneNumber;
}
