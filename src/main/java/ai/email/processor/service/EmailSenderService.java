package ai.email.processor.service;

import ai.email.processor.entity.EmailAccount;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class EmailSenderService {

    private static final Logger logger = LoggerFactory.getLogger(EmailSenderService.class);

    public void sendEmail(EmailAccount account, String to, String subject, String body) {
        logger.debug("Preparing to send email from {} to {}", account.getEmailAddress(), to);
        logger.debug("SMTP Settings - Host: {}, Port: {}, SSL: {}",
            account.getSmtpHost(), account.getSmtpPort(), account.isUseSSL());
        logger.debug("Subject: {}", subject);
        logger.debug("Body length: {} characters", body.length());

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", account.getSmtpHost());
            props.put("mail.smtp.port", account.getSmtpPort());
            props.put("mail.debug", "false"); // Set to true for JavaMail debug output

            // Port-based configuration takes priority (587=STARTTLS, 465=SSL)
            if (account.getSmtpPort() == 587) {
                // STARTTLS for port 587 (Outlook, Gmail, Yahoo standard)
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                logger.debug("Using STARTTLS for port 587");
            } else if (account.getSmtpPort() == 465) {
                // SSL for port 465
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                logger.debug("Using SSL for port 465");
            } else if (account.isUseSSL()) {
                // SSL for other ports if explicitly enabled
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", "*");
                props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                logger.debug("Using SSL for port {} (useSSL enabled)", account.getSmtpPort());
            } else {
                // Enable STARTTLS for other ports as fallback
                props.put("mail.smtp.starttls.enable", "true");
                logger.debug("Using STARTTLS as fallback for port {}", account.getSmtpPort());
            }

            logger.debug("Creating SMTP session with authentication");
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(account.getUsername(), account.getPassword());
                }
            });

            logger.debug("Building email message");
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(account.getEmailAddress()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            logger.debug("Sending email via SMTP...");
            Transport.send(message);

            logger.info("✓ Email sent successfully from {} to {}", account.getEmailAddress(), to);
        } catch (AuthenticationFailedException e) {
            logger.error("✗ SMTP Authentication failed for {}", account.getEmailAddress());
            logger.error("Check:");
            logger.error("  - SMTP username/password are correct");
            logger.error("  - App-specific password if 2FA is enabled");
            logger.error("  - SMTP access is enabled in email provider");
            throw new RuntimeException("SMTP Authentication failed", e);
        } catch (MessagingException e) {
            logger.error("✗ Failed to send email from {} to {}: {}", account.getEmailAddress(), to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendReply(EmailAccount account, String to, String originalSubject, String body) {
        String replySubject = originalSubject.startsWith("Re:") ? originalSubject : "Re: " + originalSubject;
        logger.info("Sending reply email - Original: '{}' -> Reply: '{}'", originalSubject, replySubject);
        sendEmail(account, to, replySubject, body);
    }
}
