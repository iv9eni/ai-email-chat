package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import jakarta.mail.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * Utility class for OAuth2 authentication with JavaMail
 */
@Component
public class OAuth2Authenticator {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Authenticator.class);

    private final OAuth2TokenService tokenService;

    public OAuth2Authenticator(OAuth2TokenService tokenService) {
        this.tokenService = tokenService;
    }

    /**
     * Create and configure a mail session for OAuth2 IMAP
     */
    public Session createImapSession(EmailAccount account) {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imap");
        props.put("mail.imap.host", account.getImapHost());
        props.put("mail.imap.port", account.getImapPort());
        props.put("mail.imap.ssl.enable", "true");
        props.put("mail.imap.ssl.trust", "*");

        // Enable OAuth2 authentication
        props.put("mail.imap.auth.mechanisms", "XOAUTH2");
        props.put("mail.imap.auth.login.disable", "true");
        props.put("mail.imap.auth.plain.disable", "true");

        // Debug settings
        props.put("mail.debug", "false");
        props.put("mail.debug.auth", "false");

        return Session.getInstance(props);
    }

    /**
     * Create and configure a mail session for OAuth2 SMTP
     */
    public Session createSmtpSession(EmailAccount account) {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", account.getSmtpHost());
        props.put("mail.smtp.port", account.getSmtpPort());

        // Use STARTTLS for port 587
        if (account.getSmtpPort() == 587) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        } else {
            props.put("mail.smtp.ssl.enable", "true");
        }

        props.put("mail.smtp.ssl.trust", "*");

        // Enable OAuth2 authentication
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.auth.mechanisms", "XOAUTH2");
        props.put("mail.smtp.auth.login.disable", "true");
        props.put("mail.smtp.auth.plain.disable", "true");

        // Debug settings
        props.put("mail.debug", "false");
        props.put("mail.debug.auth", "false");

        return Session.getInstance(props);
    }

    /**
     * Connect to IMAP store using OAuth2
     */
    public Store connectImap(EmailAccount account) throws MessagingException {
        // Refresh token if needed
        tokenService.refreshTokenIfNeeded(account);

        // Get valid access token
        String accessToken = tokenService.getValidAccessToken(account);
        if (accessToken == null) {
            throw new AuthenticationFailedException("Unable to get valid OAuth2 access token");
        }

        Session session = createImapSession(account);
        Store store = session.getStore("imap");

        // Connect using OAuth2
        // For XOAUTH2, we pass the access token as the password
        store.connect(account.getImapHost(),
                      account.getUsername(),
                      accessToken);

        logger.info("Successfully connected to IMAP using OAuth2 for {}", account.getEmailAddress());
        return store;
    }

    /**
     * Connect to SMTP transport using OAuth2
     */
    public Transport connectSmtp(EmailAccount account) throws MessagingException {
        // Refresh token if needed
        tokenService.refreshTokenIfNeeded(account);

        // Get valid access token
        String accessToken = tokenService.getValidAccessToken(account);
        if (accessToken == null) {
            throw new AuthenticationFailedException("Unable to get valid OAuth2 access token");
        }

        Session session = createSmtpSession(account);
        Transport transport = session.getTransport("smtp");

        // Connect using OAuth2
        // For XOAUTH2, we pass the access token as the password
        transport.connect(account.getSmtpHost(),
                          account.getUsername(),
                          accessToken);

        logger.info("Successfully connected to SMTP using OAuth2 for {}", account.getEmailAddress());
        return transport;
    }

    /**
     * Create a basic auth session for IMAP (non-OAuth2 accounts)
     */
    public Session createBasicImapSession(EmailAccount account) {
        Properties props = new Properties();
        props.put("mail.store.protocol", account.isUseSSL() ? "imaps" : "imap");
        props.put("mail.imap.host", account.getImapHost());
        props.put("mail.imap.port", account.getImapPort());
        props.put("mail.debug", "false");

        if (account.isUseSSL()) {
            props.put("mail.imap.ssl.enable", "true");
            props.put("mail.imap.ssl.trust", "*");
        } else {
            props.put("mail.imap.starttls.enable", "true");
        }

        return Session.getInstance(props);
    }

    /**
     * Create a basic auth session for SMTP (non-OAuth2 accounts)
     */
    public Session createBasicSmtpSession(EmailAccount account) {
        Properties props = new Properties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.host", account.getSmtpHost());
        props.put("mail.smtp.port", account.getSmtpPort());
        props.put("mail.smtp.auth", "true");
        props.put("mail.debug", "false");

        if (account.getSmtpPort() == 465 || account.isUseSSL()) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.trust", "*");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        }

        return Session.getInstance(props);
    }
}
