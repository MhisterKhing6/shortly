package shortly.mandmcorp.dev.shortly.service.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationRequestTemplate {

    private String to;
    private String subject;
    private String body;

    /**
     * True when body carries an OTP/verification code — MNotify bills these under a
     * separate "otp" sms_type (0.035/SMS deducted from the wallet).
     */
    private boolean otp;

}
