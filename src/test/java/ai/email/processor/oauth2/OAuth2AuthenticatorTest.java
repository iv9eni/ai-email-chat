package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.Transport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2AuthenticatorTest {

    @Mock
    private OAuth2TokenService tokenService;

    @Mock
    private EmailAccountRepository accountRepository;

    private OAuth2Authenticator authenticator;

    @BeforeEach
    void setUp() {
        authenticator = new OAuth2Authenticator(tokenService);
    }

    @Test
    void testCreateImapSession_OAuth2Account() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        // Act
        Session session = authenticator.createImapSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("imap", session.getProperty("mail.store.protocol"));
        assertEquals("outlook.office365.com", session.getProperty("mail.imap.host"));
        assertEquals("993", session.getProperty("mail.imap.port"));
        assertEquals("true", session.getProperty("mail.imap.ssl.enable"));
        assertEquals("XOAUTH2", session.getProperty("mail.imap.auth.mechanisms"));
        assertEquals("true", session.getProperty("mail.imap.auth.login.disable"));
        assertEquals("true", session.getProperty("mail.imap.auth.plain.disable"));
    }

    @Test
    void testCreateSmtpSession_OAuth2Account_Port587() {
        // Arrange
        EmailAccount account = createOAuth2Account();
        account.setSmtpPort(587);

        // Act
        Session session = authenticator.createSmtpSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("smtp", session.getProperty("mail.transport.protocol"));
        assertEquals("smtp-mail.outlook.com", session.getProperty("mail.smtp.host"));
        assertEquals("587", session.getProperty("mail.smtp.port"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", session.getProperty("mail.smtp.auth"));
        assertEquals("XOAUTH2", session.getProperty("mail.smtp.auth.mechanisms"));
    }

    @Test
    void testCreateSmtpSession_OAuth2Account_Port465() {
        // Arrange
        EmailAccount account = createOAuth2Account();
        account.setSmtpPort(465);

        // Act
        Session session = authenticator.createSmtpSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("true", session.getProperty("mail.smtp.ssl.enable"));
        assertNull(session.getProperty("mail.smtp.starttls.enable"));
    }

    @Test
    void testCreateBasicImapSession_WithSSL() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();
        account.setUseSSL(true);

        // Act
        Session session = authenticator.createBasicImapSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("imaps", session.getProperty("mail.store.protocol"));
        assertEquals("true", session.getProperty("mail.imap.ssl.enable"));
        assertNull(session.getProperty("mail.imap.auth.mechanisms")); // No OAuth2
    }

    @Test
    void testCreateBasicImapSession_WithoutSSL() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();
        account.setUseSSL(false);

        // Act
        Session session = authenticator.createBasicImapSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("imap", session.getProperty("mail.store.protocol"));
        assertEquals("true", session.getProperty("mail.imap.starttls.enable"));
        assertNull(session.getProperty("mail.imap.ssl.enable"));
    }

    @Test
    void testCreateBasicSmtpSession_Port587() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();
        account.setSmtpPort(587);

        // Act
        Session session = authenticator.createBasicSmtpSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("587", session.getProperty("mail.smtp.port"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", session.getProperty("mail.smtp.starttls.required"));
        assertNull(session.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void testCreateBasicSmtpSession_Port465() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();
        account.setSmtpPort(465);

        // Act
        Session session = authenticator.createBasicSmtpSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("465", session.getProperty("mail.smtp.port"));
        assertEquals("true", session.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void testCreateBasicSmtpSession_WithSSLEnabled() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();
        account.setUseSSL(true);
        account.setSmtpPort(993); // Non-standard port with SSL

        // Act
        Session session = authenticator.createBasicSmtpSession(account);

        // Assert
        assertNotNull(session);
        assertEquals("true", session.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void testConnectImap_RefreshesTokenIfNeeded() throws Exception {
        // Arrange
        EmailAccount account = createOAuth2Account();
        String validToken = "valid-access-token";

        when(tokenService.refreshTokenIfNeeded(account)).thenReturn(false);
        when(tokenService.getValidAccessToken(account)).thenReturn(validToken);

        // Act & Assert - We can't fully test connection without real IMAP server,
        // but we can verify the token service is called correctly
        try {
            // This will fail to connect (no real IMAP server), but we verify the setup
            authenticator.connectImap(account);
            fail("Should throw exception - no real IMAP server");
        } catch (Exception e) {
            // Expected - no real server
            verify(tokenService, times(1)).refreshTokenIfNeeded(account);
            verify(tokenService, times(1)).getValidAccessToken(account);
        }
    }

    @Test
    void testConnectImap_ThrowsExceptionIfNoValidToken() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        when(tokenService.refreshTokenIfNeeded(account)).thenReturn(false);
        when(tokenService.getValidAccessToken(account)).thenReturn(null);

        // Act & Assert
        assertThrows(AuthenticationFailedException.class, () -> {
            authenticator.connectImap(account);
        });

        verify(tokenService, times(1)).refreshTokenIfNeeded(account);
        verify(tokenService, times(1)).getValidAccessToken(account);
    }

    @Test
    void testConnectSmtp_RefreshesTokenIfNeeded() {
        // Arrange
        EmailAccount account = createOAuth2Account();
        String validToken = "valid-access-token";

        when(tokenService.refreshTokenIfNeeded(account)).thenReturn(false);
        when(tokenService.getValidAccessToken(account)).thenReturn(validToken);

        // Act & Assert - Similar to IMAP test
        try {
            authenticator.connectSmtp(account);
            fail("Should throw exception - no real SMTP server");
        } catch (Exception e) {
            // Expected - no real server
            verify(tokenService, times(1)).refreshTokenIfNeeded(account);
            verify(tokenService, times(1)).getValidAccessToken(account);
        }
    }

    @Test
    void testConnectSmtp_ThrowsExceptionIfNoValidToken() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        when(tokenService.refreshTokenIfNeeded(account)).thenReturn(false);
        when(tokenService.getValidAccessToken(account)).thenReturn(null);

        // Act & Assert
        assertThrows(AuthenticationFailedException.class, () -> {
            authenticator.connectSmtp(account);
        });
    }

    @Test
    void testImapSession_DisablesPlainAndLoginAuth() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        // Act
        Session session = authenticator.createImapSession(account);

        // Assert - Verify security: only OAuth2 should be allowed
        assertEquals("XOAUTH2", session.getProperty("mail.imap.auth.mechanisms"));
        assertEquals("true", session.getProperty("mail.imap.auth.login.disable"));
        assertEquals("true", session.getProperty("mail.imap.auth.plain.disable"));
    }

    @Test
    void testSmtpSession_DisablesPlainAndLoginAuth() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        // Act
        Session session = authenticator.createSmtpSession(account);

        // Assert - Verify security: only OAuth2 should be allowed
        assertEquals("XOAUTH2", session.getProperty("mail.smtp.auth.mechanisms"));
        assertEquals("true", session.getProperty("mail.smtp.auth.login.disable"));
        assertEquals("true", session.getProperty("mail.smtp.auth.plain.disable"));
    }

    @Test
    void testBasicSmtpSession_AllowsNormalAuth() {
        // Arrange
        EmailAccount account = createBasicAuthAccount();

        // Act
        Session session = authenticator.createBasicSmtpSession(account);

        // Assert - Basic auth should NOT disable PLAIN/LOGIN
        assertNull(session.getProperty("mail.smtp.auth.mechanisms"));
        assertNull(session.getProperty("mail.smtp.auth.login.disable"));
        assertNull(session.getProperty("mail.smtp.auth.plain.disable"));
        assertEquals("true", session.getProperty("mail.smtp.auth")); // Auth enabled
    }

    @Test
    void testSessionProperties_NoDebugByDefault() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        // Act
        Session imapSession = authenticator.createImapSession(account);
        Session smtpSession = authenticator.createSmtpSession(account);

        // Assert - Debug should be disabled by default for performance
        assertEquals("false", imapSession.getProperty("mail.debug"));
        assertEquals("false", smtpSession.getProperty("mail.debug"));
        assertEquals("false", imapSession.getProperty("mail.debug.auth"));
        assertEquals("false", smtpSession.getProperty("mail.debug.auth"));
    }

    @Test
    void testSessionProperties_TrustAllCertificates() {
        // Arrange
        EmailAccount account = createOAuth2Account();

        // Act
        Session imapSession = authenticator.createImapSession(account);
        Session smtpSession = authenticator.createSmtpSession(account);

        // Assert - Should trust all SSL certificates (for development)
        assertEquals("*", imapSession.getProperty("mail.imap.ssl.trust"));
        assertEquals("*", smtpSession.getProperty("mail.smtp.ssl.trust"));
    }

    // Helper methods
    private EmailAccount createOAuth2Account() {
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setEmailAddress("test@outlook.com");
        account.setUsername("test@outlook.com");
        account.setAuthType("oauth2");
        account.setProvider("microsoft");
        account.setAccessToken("current-access-token");
        account.setRefreshToken("current-refresh-token");
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1));
        account.setImapHost("outlook.office365.com");
        account.setImapPort(993);
        account.setSmtpHost("smtp-mail.outlook.com");
        account.setSmtpPort(587);
        account.setUseSSL(true);
        return account;
    }

    private EmailAccount createBasicAuthAccount() {
        EmailAccount account = new EmailAccount();
        account.setId(2L);
        account.setEmailAddress("test@gmail.com");
        account.setUsername("test@gmail.com");
        account.setPassword("app-password");
        account.setAuthType("basic");
        account.setImapHost("imap.gmail.com");
        account.setImapPort(993);
        account.setSmtpHost("smtp.gmail.com");
        account.setSmtpPort(587);
        account.setUseSSL(true);
        return account;
    }
}
