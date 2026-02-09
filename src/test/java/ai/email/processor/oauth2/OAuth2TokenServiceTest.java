package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
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
class OAuth2TokenServiceTest {

    @Mock
    private EmailAccountRepository accountRepository;

    @Mock
    private OAuth2Provider mockProvider;

    private OAuth2TokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new OAuth2TokenService(accountRepository);
        when(mockProvider.getProviderName()).thenReturn("test-provider");
        tokenService.registerProvider(mockProvider);
    }

    @Test
    void testRegisterProvider() {
        OAuth2Provider provider = tokenService.getProvider("test-provider");
        assertNotNull(provider);
        assertEquals("test-provider", provider.getProviderName());
    }

    @Test
    void testStoreTokens() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setEmailAddress("test@example.com");

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("test-access-token");
        tokenResponse.setRefreshToken("test-refresh-token");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(accountRepository.save(any(EmailAccount.class))).thenReturn(account);

        // Act
        tokenService.storeTokens(account, tokenResponse);

        // Assert
        assertEquals("test-access-token", account.getAccessToken());
        assertEquals("test-refresh-token", account.getRefreshToken());
        assertEquals("oauth2", account.getAuthType());
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void testRefreshTokenIfNeeded_TokenNotExpired() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setAuthType("oauth2");
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Token valid for 1 hour

        // Act
        boolean refreshed = tokenService.refreshTokenIfNeeded(account);

        // Assert
        assertFalse(refreshed); // Should not refresh
        verify(mockProvider, never()).refreshAccessToken(any());
    }

    @Test
    void testRefreshTokenIfNeeded_TokenExpired() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setAuthType("oauth2");
        account.setProvider("test-provider");
        account.setRefreshToken("old-refresh-token");
        account.setTokenExpiresAt(LocalDateTime.now().minusMinutes(1)); // Expired 1 min ago

        OAuth2TokenResponse newTokenResponse = new OAuth2TokenResponse();
        newTokenResponse.setAccessToken("new-access-token");
        newTokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(mockProvider.refreshAccessToken("old-refresh-token")).thenReturn(newTokenResponse);
        when(accountRepository.save(any(EmailAccount.class))).thenReturn(account);

        // Act
        boolean refreshed = tokenService.refreshTokenIfNeeded(account);

        // Assert
        assertTrue(refreshed);
        assertEquals("new-access-token", account.getAccessToken());
        verify(mockProvider, times(1)).refreshAccessToken("old-refresh-token");
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void testRefreshTokenIfNeeded_TokenExpiringSoon() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setAuthType("oauth2");
        account.setProvider("test-provider");
        account.setRefreshToken("old-refresh-token");
        account.setTokenExpiresAt(LocalDateTime.now().plusMinutes(3)); // Expires in 3 min (< 5 min threshold)

        OAuth2TokenResponse newTokenResponse = new OAuth2TokenResponse();
        newTokenResponse.setAccessToken("new-access-token");
        newTokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(mockProvider.refreshAccessToken("old-refresh-token")).thenReturn(newTokenResponse);
        when(accountRepository.save(any(EmailAccount.class))).thenReturn(account);

        // Act
        boolean refreshed = tokenService.refreshTokenIfNeeded(account);

        // Assert
        assertTrue(refreshed); // Should refresh because < 5 min until expiry
        verify(mockProvider, times(1)).refreshAccessToken("old-refresh-token");
    }

    @Test
    void testRefreshTokenIfNeeded_BasicAuthAccount() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setAuthType("basic");

        // Act
        boolean refreshed = tokenService.refreshTokenIfNeeded(account);

        // Assert
        assertFalse(refreshed); // Basic auth accounts don't get refreshed
        verify(mockProvider, never()).refreshAccessToken(any());
    }

    @Test
    void testGetValidAccessToken() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setAuthType("oauth2");
        account.setAccessToken("valid-token");
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1)); // Valid for 1 hour

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        // Act
        String token = tokenService.getValidAccessToken(account);

        // Assert
        assertEquals("valid-token", token);
    }

    @Test
    void testGetValidAccessToken_NullForBasicAuth() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setAuthType("basic");

        // Act
        String token = tokenService.getValidAccessToken(account);

        // Assert
        assertNull(token); // Basic auth accounts don't have access tokens
    }

    @Test
    void testRevokeAccess() {
        // Arrange
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setAuthType("oauth2");
        account.setAccessToken("token-to-revoke");
        account.setRefreshToken("refresh-to-revoke");
        account.setTokenExpiresAt(LocalDateTime.now().plusHours(1));

        when(accountRepository.save(any(EmailAccount.class))).thenReturn(account);

        // Act
        tokenService.revokeAccess(account);

        // Assert
        assertNull(account.getAccessToken());
        assertNull(account.getRefreshToken());
        assertNull(account.getTokenExpiresAt());
        assertEquals("basic", account.getAuthType());
        verify(accountRepository, times(1)).save(account);
    }

    @Test
    void testGetAvailableProviders() {
        // Act
        String[] providers = tokenService.getAvailableProviders();

        // Assert
        assertEquals(1, providers.length);
        assertEquals("test-provider", providers[0]);
    }
}
