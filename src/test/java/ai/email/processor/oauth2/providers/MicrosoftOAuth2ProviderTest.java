package ai.email.processor.oauth2.providers;

import ai.email.processor.oauth2.OAuth2TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MicrosoftOAuth2ProviderTest {

    @Mock
    private RestTemplate restTemplate;

    private MicrosoftOAuth2Provider provider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        provider = new MicrosoftOAuth2Provider();

        // Set test client ID and secret
        ReflectionTestUtils.setField(provider, "clientId", "test-client-id");
        ReflectionTestUtils.setField(provider, "clientSecret", "test-client-secret");

        // Inject mock RestTemplate
        ReflectionTestUtils.setField(provider, "restTemplate", restTemplate);
    }

    @Test
    void testGetProviderName() {
        assertEquals("microsoft", provider.getProviderName());
    }

    @Test
    void testGetAuthorizationUrl() {
        String redirectUri = "http://localhost:8080/oauth2/callback/microsoft";
        String state = "test-state-123";

        String authUrl = provider.getAuthorizationUrl(redirectUri, state);

        assertNotNull(authUrl);
        assertTrue(authUrl.contains("login.microsoftonline.com"));
        assertTrue(authUrl.contains("client_id=test-client-id"));
        assertTrue(authUrl.contains("redirect_uri=" + redirectUri));
        assertTrue(authUrl.contains("state=" + state));
        assertTrue(authUrl.contains("response_type=code"));
        assertTrue(authUrl.contains("scope="));
        assertTrue(authUrl.contains("IMAP.AccessAsUser.All"));
        assertTrue(authUrl.contains("SMTP.Send"));
        assertTrue(authUrl.contains("offline_access"));
    }

    @Test
    void testExchangeCodeForTokens() {
        // Arrange
        String code = "test-auth-code";
        String redirectUri = "http://localhost:8080/oauth2/callback/microsoft";

        String mockResponse = """
            {
                "access_token": "test-access-token",
                "refresh_token": "test-refresh-token",
                "expires_in": 3600,
                "token_type": "Bearer",
                "scope": "IMAP.AccessAsUser.All SMTP.Send offline_access"
            }
            """;

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        OAuth2TokenResponse response = provider.exchangeCodeForTokens(code, redirectUri);

        // Assert
        assertNotNull(response);
        assertEquals("test-access-token", response.getAccessToken());
        assertEquals("test-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getExpiresAt());

        // Verify expiry is approximately 1 hour from now
        LocalDateTime expectedExpiry = LocalDateTime.now().plusSeconds(3600);
        assertTrue(response.getExpiresAt().isAfter(LocalDateTime.now()));
        assertTrue(response.getExpiresAt().isBefore(LocalDateTime.now().plusHours(2)));

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void testRefreshAccessToken() {
        // Arrange
        String refreshToken = "old-refresh-token";

        String mockResponse = """
            {
                "access_token": "new-access-token",
                "refresh_token": "new-refresh-token",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
            """;

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        OAuth2TokenResponse response = provider.refreshAccessToken(refreshToken);

        // Assert
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertNotNull(response.getExpiresAt());

        verify(restTemplate, times(1)).postForEntity(anyString(), any(), eq(String.class));
    }

    @Test
    void testGetUserEmail() {
        // Arrange
        String accessToken = "test-access-token";

        String mockResponse = """
            {
                "userPrincipalName": "test@outlook.com",
                "displayName": "Test User",
                "id": "12345"
            }
            """;

        when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        String email = provider.getUserEmail(accessToken);

        // Assert
        assertEquals("test@outlook.com", email);
        verify(restTemplate, times(1)).exchange(anyString(), any(), any(), eq(String.class));
    }

    @Test
    void testGetDefaultImapHost() {
        assertEquals("outlook.office365.com", provider.getDefaultImapHost());
    }

    @Test
    void testGetDefaultImapPort() {
        assertEquals(993, provider.getDefaultImapPort());
    }

    @Test
    void testGetDefaultSmtpHost() {
        assertEquals("smtp-mail.outlook.com", provider.getDefaultSmtpHost());
    }

    @Test
    void testGetDefaultSmtpPort() {
        assertEquals(587, provider.getDefaultSmtpPort());
    }

    @Test
    void testGetDefaultUseSSL() {
        assertTrue(provider.getDefaultUseSSL());
    }

    @Test
    void testGetRequiredScopes() {
        String[] scopes = provider.getRequiredScopes();

        assertNotNull(scopes);
        assertEquals(4, scopes.length);

        // Verify all required scopes are present
        assertTrue(containsScope(scopes, "IMAP.AccessAsUser.All"));
        assertTrue(containsScope(scopes, "SMTP.Send"));
        assertTrue(containsScope(scopes, "offline_access"));
        assertTrue(containsScope(scopes, "User.Read"));
    }

    @Test
    void testExchangeCodeForTokens_WithoutRefreshToken() {
        // Some OAuth responses might not include a refresh token
        String code = "test-auth-code";
        String redirectUri = "http://localhost:8080/oauth2/callback/microsoft";

        String mockResponse = """
            {
                "access_token": "test-access-token",
                "expires_in": 3600,
                "token_type": "Bearer"
            }
            """;

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        OAuth2TokenResponse response = provider.exchangeCodeForTokens(code, redirectUri);

        // Assert
        assertNotNull(response);
        assertEquals("test-access-token", response.getAccessToken());
        assertNull(response.getRefreshToken()); // Should handle missing refresh token
    }

    @Test
    void testTokenExchange_HandlesExpiryCorrectly() {
        // Arrange
        String code = "test-auth-code";
        String redirectUri = "http://localhost:8080/oauth2/callback/microsoft";

        // Test with different expiry times
        String mockResponse = """
            {
                "access_token": "test-token",
                "refresh_token": "test-refresh",
                "expires_in": 7200,
                "token_type": "Bearer"
            }
            """;

        when(restTemplate.postForEntity(anyString(), any(), eq(String.class)))
            .thenReturn(new ResponseEntity<>(mockResponse, HttpStatus.OK));

        // Act
        OAuth2TokenResponse response = provider.exchangeCodeForTokens(code, redirectUri);

        // Assert
        LocalDateTime expectedExpiry = LocalDateTime.now().plusSeconds(7200);
        assertTrue(response.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(119)));
        assertTrue(response.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(121)));
    }

    @Test
    void testAuthorizationUrl_ContainsAllRequiredParameters() {
        String redirectUri = "http://localhost:8080/oauth2/callback/microsoft";
        String state = "random-state";

        String authUrl = provider.getAuthorizationUrl(redirectUri, state);

        // Verify all critical OAuth2 parameters
        assertTrue(authUrl.contains("client_id="));
        assertTrue(authUrl.contains("response_type=code"));
        assertTrue(authUrl.contains("redirect_uri="));
        assertTrue(authUrl.contains("response_mode=query"));
        assertTrue(authUrl.contains("scope="));
        assertTrue(authUrl.contains("state="));
    }

    // Helper method
    private boolean containsScope(String[] scopes, String scope) {
        for (String s : scopes) {
            if (s.contains(scope)) {
                return true;
            }
        }
        return false;
    }
}
