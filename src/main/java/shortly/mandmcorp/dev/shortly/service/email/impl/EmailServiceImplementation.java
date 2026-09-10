package shortly.mandmcorp.dev.shortly.service.email.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import shortly.mandmcorp.dev.shortly.service.email.EmailServiceInterface;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceImplementation implements EmailServiceInterface {

    private final JavaMailSender mailSender;

    @Value("${app.mail-from}")
    private String mailFrom;

    @Override
    @Async("taskExecutor")
    public void sendCompanyVerificationEmail(String toEmail, String recipientName, String companyName, String verificationLink) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Verify your email to activate " + companyName);
            helper.setText(buildHtml(recipientName, companyName, verificationLink), true);

            mailSender.send(message);
            log.info("Verification email sent to {}", toEmail);
        } catch (Exception e) {
            // Runs on a background thread — log and swallow so it never breaks the request flow.
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    @Override
    @Async("taskExecutor")
    public void sendUserCredentialsEmail(String toEmail, String recipientName, String phoneNumber, String password, String role) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(mailFrom);
            helper.setTo(toEmail);
            helper.setSubject("Your account login details");
            helper.setText(buildCredentialsHtml(recipientName, phoneNumber, password, role), true);

            mailSender.send(message);
            log.info("Credentials email sent to {}", toEmail);
        } catch (Exception e) {
            // Runs on a background thread — log and swallow so it never breaks the request flow.
            log.error("Failed to send credentials email to {}: {}", toEmail, e.getMessage());
        }
    }

    private String buildCredentialsHtml(String recipientName, String phoneNumber, String password, String role) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 24px;">
              <h2 style="color: #111;">Welcome, %s</h2>
              <p>An account has been created for you (role: <strong>%s</strong>). Use the details below to log in:</p>
              <table style="border-collapse: collapse; margin: 16px 0;">
                <tr><td style="padding: 6px 12px; color: #666;">Phone number</td><td style="padding: 6px 12px;"><strong>%s</strong></td></tr>
                <tr><td style="padding: 6px 12px; color: #666;">Password</td><td style="padding: 6px 12px;"><strong>%s</strong></td></tr>
              </table>
              <p style="color: #999; font-size: 12px;">For your security, please change your password after your first login.</p>
            </div>
            """.formatted(recipientName, role, phoneNumber, password);
    }

    private String buildHtml(String recipientName, String companyName, String verificationLink) {
        return """
            <div style="font-family: Arial, sans-serif; max-width: 480px; margin: auto; padding: 24px;">
              <h2 style="color: #111;">Confirm your email</h2>
              <p>Hi %s,</p>
              <p>Thanks for registering <strong>%s</strong>. Please confirm your email address to activate your company account and create your admin login.</p>
              <p style="margin: 28px 0;">
                <a href="%s" style="background: #2563eb; color: #fff; padding: 12px 20px; border-radius: 6px; text-decoration: none;">Verify email</a>
              </p>
              <p style="color: #666; font-size: 13px;">If the button doesn't work, copy and paste this link into your browser:</p>
              <p style="color: #2563eb; font-size: 13px; word-break: break-all;">%s</p>
              <p style="color: #999; font-size: 12px;">If you didn't request this, you can safely ignore this email.</p>
            </div>
            """.formatted(recipientName, companyName, verificationLink, verificationLink);
    }
}
