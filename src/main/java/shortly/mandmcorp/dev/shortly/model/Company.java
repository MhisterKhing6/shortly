package shortly.mandmcorp.dev.shortly.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.Data;

@Data
@Document(collection = "companies")
public class Company {

    @Id
    private String id;

    @Indexed
    private String companyName;

    @Indexed
    private String displayName;

    private String companyLogo;

    private String primaryColor;

    private String secondaryColor;

    private String description;

    private String address;

    private String managerName;

    private String managerPhoneNumber;

    @Indexed(unique = true)
    private String email;

    private String phoneNumber;

    private String registrationNumber;

    private boolean emailVerified = false;

    private boolean enabled = false;

    @CreatedDate
    private Long createdAt;

    @LastModifiedDate
    private Long updatedAt;
}
