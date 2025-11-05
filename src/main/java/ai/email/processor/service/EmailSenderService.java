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
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", account.getSmtpHost());
            props.put("mail.smtp.port", account.getSmtpPort());

            if (account.isUseSSL()) {
                props.put("mail.smtp.ssl.enable", "true");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(account.getUsername(), account.getPassword());
                }
            });

            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(account.getEmailAddress()));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);
            message.setText(body);

            Transport.send(message);

            logger.info("Email sent successfully from {} to {}", account.getEmailAddress(), to);
        } catch (MessagingException e) {
            logger.error("Failed to send email from {} to {}", account.getEmailAddress(), to, e);
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendReply(EmailAccount account, String to, String originalSubject, String body) {
        String replySubject = originalSubject.startsWith("Re:") ? originalSubject : "Re: " + originalSubject;
        sendEmail(account, to, replySubject, body);
    }
}
