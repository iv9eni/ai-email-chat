package ai.email.processor.controller;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.service.EmailAccountService;
import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticsController.class);

    private final EmailAccountService emailAccountService;

    public DiagnosticsController(EmailAccountService emailAccountService) {
        this.emailAccountService = emailAccountService;
    }

    @GetMapping("/test-connection/{accountId}")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable Long accountId) {
        logger.info("Testing connection for account ID: {}", accountId);

        Optional<EmailAccount> accountOpt = emailAccountService.getAccount(accountId);
        if (accountOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        EmailAccount account = accountOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("accountEmail", account.getEmailAddress());
        result.put("timestamp", new Date());

        // Test IMAP connection
        Map<String, Object> imapTest = testIMAP(account);
        result.put("imap", imapTest);

        // Test SMTP connection
        Map<String, Object> smtpTest = testSMTP(account);
        result.put("smtp", smtpTest);

        boolean allSuccess = (boolean) imapTest.get("success") && (boolean) smtpTest.get("success");
        result.put("overallStatus", allSuccess ? "SUCCESS" : "FAILED");

        return ResponseEntity.ok(result);
    }

    private Map<String, Object> testIMAP(EmailAccount account) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocol", "IMAP");
        result.put("host", account.getImapHost());
        result.put("port", account.getImapPort());
        result.put("ssl", account.isUseSSL());

        try {
            Properties props = new Properties();
            props.put("mail.store.protocol", account.isUseSSL() ? "imaps" : "imap");
            props.put("mail.imap.host", account.getImapHost());
            props.put("mail.imap.port", account.getImapPort());
            props.put("mail.imap.connectiontimeout", "5000");
            props.put("mail.imap.timeout", "5000");

            if (account.isUseSSL()) {
                props.put("mail.imap.ssl.enable", "true");
                props.put("mail.imap.ssl.trust", "*");
            } else {
                props.put("mail.imap.starttls.enable", "true");
            }

            Session session = Session.getInstance(props);
            Store store = session.getStore();

            logger.debug("Attempting IMAP connection to {}:{}", account.getImapHost(), account.getImapPort());
            store.connect(account.getImapHost(), account.getUsername(), account.getPassword());

            if (store.isConnected()) {
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_ONLY);

                result.put("success", true);
                result.put("message", "Connection successful");
                result.put("totalMessages", inbox.getMessageCount());
                result.put("unreadMessages", inbox.getUnreadMessageCount());

                inbox.close(false);
                store.close();
                logger.info("✓ IMAP test successful for {}", account.getEmailAddress());
            } else {
                result.put("success", false);
                result.put("message", "Failed to connect");
                logger.warn("✗ IMAP connection failed for {}", account.getEmailAddress());
            }
        } catch (AuthenticationFailedException e) {
            result.put("success", false);
            result.put("error", "Authentication Failed");
            result.put("message", "Check your username/password and 2FA settings");
            result.put("details", e.getMessage());
            logger.error("✗ IMAP authentication failed for {}", account.getEmailAddress(), e);
        } catch (MessagingException e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
            logger.error("✗ IMAP error for {}", account.getEmailAddress(), e);
        }

        return result;
    }

    private Map<String, Object> testSMTP(EmailAccount account) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocol", "SMTP");
        result.put("host", account.getSmtpHost());
        result.put("port", account.getSmtpPort());
        result.put("ssl", account.isUseSSL());

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.host", account.getSmtpHost());
            props.put("mail.smtp.port", account.getSmtpPort());
            props.put("mail.smtp.connectiontimeout", "5000");
            props.put("mail.smtp.timeout", "5000");

            if (account.isUseSSL()) {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.ssl.trust", "*");
            }

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(account.getUsername(), account.getPassword());
                }
            });

            logger.debug("Attempting SMTP connection to {}:{}", account.getSmtpHost(), account.getSmtpPort());
            Transport transport = session.getTransport("smtp");
            transport.connect(account.getSmtpHost(), account.getUsername(), account.getPassword());

            if (transport.isConnected()) {
                result.put("success", true);
                result.put("message", "Connection successful");
                transport.close();
                logger.info("✓ SMTP test successful for {}", account.getEmailAddress());
            } else {
                result.put("success", false);
                result.put("message", "Failed to connect");
                logger.warn("✗ SMTP connection failed for {}", account.getEmailAddress());
            }
        } catch (AuthenticationFailedException e) {
            result.put("success", false);
            result.put("error", "Authentication Failed");
            result.put("message", "Check your username/password and 2FA settings");
            result.put("details", e.getMessage());
            logger.error("✗ SMTP authentication failed for {}", account.getEmailAddress(), e);
        } catch (MessagingException e) {
            result.put("success", false);
            result.put("error", e.getClass().getSimpleName());
            result.put("message", e.getMessage());
            logger.error("✗ SMTP error for {}", account.getEmailAddress(), e);
        }

        return result;
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        List<EmailAccount> allAccounts = emailAccountService.getAllAccounts();
        List<EmailAccount> activeAccounts = emailAccountService.getActiveAccounts();

        status.put("totalAccounts", allAccounts.size());
        status.put("activeAccounts", activeAccounts.size());
        status.put("timestamp", new Date());

        // Account summaries
        List<Map<String, Object>> accountSummaries = new ArrayList<>();
        for (EmailAccount account : allAccounts) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("id", account.getId());
            summary.put("email", account.getEmailAddress());
            summary.put("active", account.isActive());
            summary.put("imapHost", account.getImapHost());
            summary.put("smtpHost", account.getSmtpHost());
            accountSummaries.add(summary);
        }
        status.put("accounts", accountSummaries);

        return ResponseEntity.ok(status);
    }
}
