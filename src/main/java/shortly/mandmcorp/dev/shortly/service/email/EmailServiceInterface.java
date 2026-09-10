package shortly.mandmcorp.dev.shortly.service.email;

public interface EmailServiceInterface {
    void sendCompanyVerificationEmail(String toEmail, String recipientName, String companyName, String verificationLink);
    void sendUserCredentialsEmail(String toEmail, String recipientName, String phoneNumber, String password, String role);
}
