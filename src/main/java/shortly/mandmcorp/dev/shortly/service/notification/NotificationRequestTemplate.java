package shortly.mandmcorp.dev.shortly.service.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationRequestTemplate {

    private String to;
    private String subject;
    private String body;

}
