package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2ControllerTest {

    @Mock
    private OAuth2TokenService tokenService;

    @Mock
    private EmailAccountRepository accountRepository;

    @Mock
    private OAuth2Provider mockProvider;

    private OAuth2Controller controller;
    private MockHttpSession session;
    private RedirectAttributesModelMap redirectAttributes;

    @BeforeEach
    void setUp() {
        controller = new OAuth2Controller(tokenService, accountRepository);
        ReflectionTestUtils.setField(controller, "redirectBaseUrl", "http://localhost:8080");

        session = new MockHttpSession();
        redirectAttributes = new RedirectAttributesModelMap();

        // Setup mock provider
        when(mockProvider.getProviderName()).thenReturn("test-provider");
        when(tokenService.getProvider("test-provider")).thenReturn(mockProvider);
    }

    @Test
    void testAuthorize_CreatesStateAndRedirects() {
        // Arrange
        when(mockProvider.getAuthorizationUrl(anyString(), anyString()))
            .thenReturn("https://oauth.provider.com/authorize?state=abc123");

        // Act
        String result = controller.authorize("test-provider", null, session);

        // Assert
        assertEquals("redirect:https://oauth.provider.com/authorize?state=abc123", result);

        // Verify session contains OAuth state
        assertNotNull(session.getAttribute("oauth2_state"));
        assertEquals("test-provider", session.getAttribute("oauth2_provider"));
        assertNull(session.getAttribute("oauth2_account_id"));

        verify(mockProvider, times(1)).getAuthorizationUrl(
            eq("http://localhost:8080/oauth2/callback/test-provider"),
            anyString()
        );
    }

    @Test
    void testAuthorize_WithAccountId_StoresAccountIdInSession() {
        // Arrange
        Long accountId = 123L;
        when(mockProvider.getAuthorizationUrl(anyString(), anyString()))
            .thenReturn("https://oauth.provider.com/authorize");

        // Act
        controller.authorize("test-provider", accountId, session);

        // Assert
        assertEquals(accountId, session.getAttribute("oauth2_account_id"));
    }

    @Test
    void testAuthorize_UnknownProvider_RedirectsWithError() {
        // Arrange
        when(tokenService.getProvider("unknown-provider")).thenReturn(null);

        // Act
        String result = controller.authorize("unknown-provider", null, session);

        // Assert
        assertEquals("redirect:/accounts?error=unknown_provider", result);
    }

    @Test
    void testCallback_Success_NewAccount() {
        // Arrange
        String code = "auth-code-123";
        String state = "test-state";
        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", "test-provider");

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("access-token");
        tokenResponse.setRefreshToken("refresh-token");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(mockProvider.exchangeCodeForTokens(eq(code), anyString()))
            .thenReturn(tokenResponse);
        when(mockProvider.getUserEmail(eq("access-token")))
            .thenReturn("test@example.com");
        when(mockProvider.getDefaultImapHost()).thenReturn("imap.example.com");
        when(mockProvider.getDefaultImapPort()).thenReturn(993);
        when(mockProvider.getDefaultSmtpHost()).thenReturn("smtp.example.com");
        when(mockProvider.getDefaultSmtpPort()).thenReturn(587);
        when(mockProvider.getDefaultUseSSL()).thenReturn(true);

        // Act
        String result = controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("success"));

        // Verify state was cleared from session
        assertNull(session.getAttribute("oauth2_state"));
        assertNull(session.getAttribute("oauth2_provider"));

        verify(tokenService, times(1)).storeTokens(any(EmailAccount.class), eq(tokenResponse));
        verify(mockProvider, times(1)).exchangeCodeForTokens(eq(code), anyString());
        verify(mockProvider, times(1)).getUserEmail(eq("access-token"));
    }

    @Test
    void testCallback_Success_ExistingAccount() {
        // Arrange
        Long accountId = 456L;
        String code = "auth-code-123";
        String state = "test-state";

        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", "test-provider");
        session.setAttribute("oauth2_account_id", accountId);

        EmailAccount existingAccount = new EmailAccount();
        existingAccount.setId(accountId);
        existingAccount.setEmailAddress("existing@example.com");

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("new-access-token");
        tokenResponse.setRefreshToken("new-refresh-token");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(existingAccount));
        when(mockProvider.exchangeCodeForTokens(eq(code), anyString()))
            .thenReturn(tokenResponse);
        when(mockProvider.getUserEmail(anyString()))
            .thenReturn("existing@example.com");

        // Act
        String result = controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("success"));

        verify(accountRepository, times(1)).findById(accountId);
        verify(tokenService, times(1)).storeTokens(eq(existingAccount), eq(tokenResponse));
    }

    @Test
    void testCallback_ErrorFromProvider() {
        // Arrange
        String error = "access_denied";

        // Act
        String result = controller.callback("test-provider", null, null, error, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        String errorMessage = (String) redirectAttributes.getFlashAttributes().get("error");
        assertTrue(errorMessage.contains("access_denied"));
    }

    @Test
    void testCallback_StateMismatch() {
        // Arrange
        String code = "auth-code";
        String state = "wrong-state";
        session.setAttribute("oauth2_state", "correct-state");
        session.setAttribute("oauth2_provider", "test-provider");

        // Act
        String result = controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        String errorMessage = (String) redirectAttributes.getFlashAttributes().get("error");
        assertTrue(errorMessage.contains("Security validation failed"));

        // Verify no token exchange happened
        verify(mockProvider, never()).exchangeCodeForTokens(anyString(), anyString());
    }

    @Test
    void testCallback_ProviderMismatch() {
        // Arrange
        String code = "auth-code";
        String state = "test-state";
        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", "expected-provider");

        // Act
        String result = controller.callback("different-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        String errorMessage = (String) redirectAttributes.getFlashAttributes().get("error");
        assertTrue(errorMessage.contains("Provider mismatch"));
    }

    @Test
    void testCallback_MissingState() {
        // Arrange
        String code = "auth-code";
        String state = "some-state";
        // Don't set state in session

        // Act
        String result = controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
    }

    @Test
    void testCallback_AccountNotFoundForReauth() {
        // Arrange
        Long accountId = 999L;
        String code = "auth-code";
        String state = "test-state";

        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", "test-provider");
        session.setAttribute("oauth2_account_id", accountId);

        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act
        String result = controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));
        String errorMessage = (String) redirectAttributes.getFlashAttributes().get("error");
        assertTrue(errorMessage.contains("Account not found"));
    }

    @Test
    void testDisconnect_Success() {
        // Arrange
        Long accountId = 123L;
        EmailAccount account = new EmailAccount();
        account.setId(accountId);
        account.setEmailAddress("test@example.com");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        String result = controller.disconnect(accountId, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("success"));

        verify(tokenService, times(1)).revokeAccess(account);
    }

    @Test
    void testDisconnect_AccountNotFound() {
        // Arrange
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act
        String result = controller.disconnect(accountId, redirectAttributes);

        // Assert
        assertEquals("redirect:/accounts", result);
        assertTrue(redirectAttributes.getFlashAttributes().containsKey("error"));

        verify(tokenService, never()).revokeAccess(any());
    }

    @Test
    void testReconnect_Success() {
        // Arrange
        Long accountId = 123L;
        EmailAccount account = new EmailAccount();
        account.setId(accountId);
        account.setAuthType("oauth2");
        account.setProvider("test-provider");

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        String result = controller.reconnect(accountId, session);

        // Assert
        assertEquals("redirect:/oauth2/authorize/test-provider?accountId=" + accountId, result);
    }

    @Test
    void testReconnect_AccountNotFound() {
        // Arrange
        Long accountId = 999L;
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        // Act
        String result = controller.reconnect(accountId, session);

        // Assert
        assertEquals("redirect:/accounts?error=invalid_account", result);
    }

    @Test
    void testReconnect_NotOAuth2Account() {
        // Arrange
        Long accountId = 123L;
        EmailAccount account = new EmailAccount();
        account.setId(accountId);
        account.setAuthType("basic"); // Not OAuth2

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        // Act
        String result = controller.reconnect(accountId, session);

        // Assert
        assertEquals("redirect:/accounts?error=invalid_account", result);
    }

    @Test
    void testCallback_ClearsSessionAttributes() {
        // Arrange
        String code = "auth-code";
        String state = "test-state";
        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", "test-provider");
        session.setAttribute("oauth2_account_id", 123L);

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("token");
        tokenResponse.setRefreshToken("refresh");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(mockProvider.exchangeCodeForTokens(anyString(), anyString())).thenReturn(tokenResponse);
        when(mockProvider.getUserEmail(anyString())).thenReturn("test@example.com");
        when(mockProvider.getDefaultImapHost()).thenReturn("imap.example.com");
        when(mockProvider.getDefaultImapPort()).thenReturn(993);
        when(mockProvider.getDefaultSmtpHost()).thenReturn("smtp.example.com");
        when(mockProvider.getDefaultSmtpPort()).thenReturn(587);
        when(mockProvider.getDefaultUseSSL()).thenReturn(true);

        // Act
        controller.callback("test-provider", code, state, null, session, redirectAttributes);

        // Assert - all OAuth session attributes should be cleared
        assertNull(session.getAttribute("oauth2_state"));
        assertNull(session.getAttribute("oauth2_provider"));
        assertNull(session.getAttribute("oauth2_account_id"));
    }
}
