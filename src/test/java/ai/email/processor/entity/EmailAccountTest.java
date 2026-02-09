package ai.email.processor.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class EmailAccountTest {

    @Test
    void testIsOAuth2_WhenOAuth2Account() {
        EmailAccount account = new EmailAccount();
        account.setAuthType("oauth2");

        assertTrue(account.isOAuth2());
    }

    @Test
    void testIsOAuth2_WhenBasicAuthAccount() {
        EmailAccount account = new EmailAccount();
        account.setAuthType("basic");

        assertFalse(account.isOAuth2());
    }

    @Test
    void testIsOAuth2_WhenAuthTypeNull() {
        EmailAccount account = new EmailAccount();
        // authType defaults to "basic"

        assertFalse(account.isOAuth2());
    }

    @Test
    void testIsTokenExpired_WhenExpired() {
        EmailAccount account = new EmailAccount();
        account.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired 1 min ago

        assertTrue(account.isTokenExpired());
    }

    @Test
    void testIsTokenExpired_WhenNotExpired() {
        EmailAccount account = new EmailAccount();
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Expires in 1 hour

        assertFalse(account.isTokenExpired());
    }

    @Test
    void testIsTokenExpired_WhenExpiryNull() {
        EmailAccount account = new EmailAccount();
        account.setTokenExpiresAt(null);

        assertFalse(account.isTokenExpired());
    }

    @Test
    void testDefaultAuthType() {
        EmailAccount account = new EmailAccount();

        assertEquals("basic", account.getAuthType());
    }

    @Test
    void testOAuth2FieldsNullable() {
        EmailAccount account = new EmailAccount();
        account.setAuthType("oauth2");
        account.setProvider("microsoft");
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setTokenExpiresAt(null);

        // Should not throw any exceptions
        assertNull(account.getAccessToken());
        assertNull(account.getRefreshToken());
        assertNull(account.getTokenExpiresAt());
    }

    @Test
    void testPasswordNullableForOAuth2() {
        EmailAccount account = new EmailAccount();
        account.setAuthType("oauth2");
        account.setPassword(null); // OAuth2 accounts don't need passwords

        assertNull(account.getPassword());
        assertTrue(account.isOAuth2());
    }
}
