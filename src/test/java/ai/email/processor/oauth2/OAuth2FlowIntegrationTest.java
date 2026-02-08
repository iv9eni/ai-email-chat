package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for OAuth2 flow
 *
 * This test uses mocked OAuth2 providers and repositories to test the full flow
 * without needing real OAuth servers or credentials.
 *
 * For testing with real OAuth servers, use WireMock (see TESTING_GUIDE.md)
 */
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2FlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuth2Provider microsoftProvider;

    @MockBean
    private EmailAccountRepository accountRepository;

    @MockBean
    private OAuth2TokenService tokenService;

    @Test
    void testCompleteOAuth2Flow_NewAccount() throws Exception {
        // Setup mocks
        when(tokenService.getProvider("microsoft")).thenReturn(microsoftProvider);
        when(microsoftProvider.getProviderName()).thenReturn("microsoft");
        when(microsoftProvider.getAuthorizationUrl(anyString(), anyString()))
            .thenReturn("https://login.microsoftonline.com/authorize?state=abc123");

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("test-access-token");
        tokenResponse.setRefreshToken("test-refresh-token");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(microsoftProvider.exchangeCodeForTokens(anyString(), anyString()))
            .thenReturn(tokenResponse);
        when(microsoftProvider.getUserEmail("test-access-token"))
            .thenReturn("test@outlook.com");
        when(microsoftProvider.getDefaultImapHost()).thenReturn("outlook.office365.com");
        when(microsoftProvider.getDefaultImapPort()).thenReturn(993);
        when(microsoftProvider.getDefaultSmtpHost()).thenReturn("smtp-mail.outlook.com");
        when(microsoftProvider.getDefaultSmtpPort()).thenReturn(587);
        when(microsoftProvider.getDefaultUseSSL()).thenReturn(true);

        // Step 1: User clicks "Connect Microsoft" - should redirect to OAuth provider
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/oauth2/authorize/microsoft"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("https://login.microsoftonline.com/*"))
            .andReturn()
            .getRequest()
            .getSession();

        // Verify session state was set
        String state = (String) session.getAttribute("oauth2_state");
        assert state != null;
        assert session.getAttribute("oauth2_provider").equals("microsoft");

        // Step 2: OAuth provider redirects back with code - should create account and redirect to /accounts
        mockMvc.perform(get("/oauth2/callback/microsoft")
                .param("code", "test-auth-code")
                .param("state", state)
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/accounts"))
            .andExpect(flash().attributeExists("success"));

        // Verify session was cleared
        assert session.getAttribute("oauth2_state") == null;
        assert session.getAttribute("oauth2_provider") == null;
    }

    @Test
    void testOAuth2Flow_ReconnectExistingAccount() throws Exception {
        // Setup existing account
        EmailAccount existingAccount = new EmailAccount();
        existingAccount.setId(1L);
        existingAccount.setAuthType("oauth2");
        existingAccount.setProvider("microsoft");
        existingAccount.setEmailAddress("existing@outlook.com");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(existingAccount));
        when(tokenService.getProvider("microsoft")).thenReturn(microsoftProvider);
        when(microsoftProvider.getProviderName()).thenReturn("microsoft");
        when(microsoftProvider.getAuthorizationUrl(anyString(), anyString()))
            .thenReturn("https://login.microsoftonline.com/authorize");

        OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse();
        tokenResponse.setAccessToken("new-access-token");
        tokenResponse.setRefreshToken("new-refresh-token");
        tokenResponse.setExpiresAt(LocalDateTime.now().plusHours(1));

        when(microsoftProvider.exchangeCodeForTokens(anyString(), anyString()))
            .thenReturn(tokenResponse);
        when(microsoftProvider.getUserEmail("new-access-token"))
            .thenReturn("existing@outlook.com");

        // Step 1: User clicks "Reconnect" on existing account
        mockMvc.perform(get("/oauth2/reconnect/1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/oauth2/authorize/microsoft*accountId=1"));

        // Step 2: Initiate OAuth flow with account ID
        MockHttpSession session = (MockHttpSession) mockMvc.perform(
                get("/oauth2/authorize/microsoft")
                    .param("accountId", "1"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getRequest()
            .getSession();

        String state = (String) session.getAttribute("oauth2_state");
        assert session.getAttribute("oauth2_account_id").equals(1L);

        // Step 3: Complete OAuth callback - should update existing account
        mockMvc.perform(get("/oauth2/callback/microsoft")
                .param("code", "test-code")
                .param("state", state)
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/accounts"))
            .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testOAuth2Flow_CSRFProtection_StateMismatch() throws Exception {
        when(tokenService.getProvider("microsoft")).thenReturn(microsoftProvider);
        when(microsoftProvider.getProviderName()).thenReturn("microsoft");
        when(microsoftProvider.getAuthorizationUrl(anyString(), anyString()))
            .thenReturn("https://login.microsoftonline.com/authorize");

        // Start OAuth flow
        MockHttpSession session = (MockHttpSession) mockMvc.perform(get("/oauth2/authorize/microsoft"))
            .andReturn()
            .getRequest()
            .getSession();

        String correctState = (String) session.getAttribute("oauth2_state");

        // Callback with wrong state - should reject
        mockMvc.perform(get("/oauth2/callback/microsoft")
                .param("code", "test-code")
                .param("state", "wrong-state")
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/accounts"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testOAuth2Flow_ErrorFromProvider() throws Exception {
        when(tokenService.getProvider("microsoft")).thenReturn(microsoftProvider);
        when(microsoftProvider.getProviderName()).thenReturn("microsoft");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("oauth2_state", "test-state");
        session.setAttribute("oauth2_provider", "microsoft");

        // Callback with error from provider
        mockMvc.perform(get("/oauth2/callback/microsoft")
                .param("error", "access_denied")
                .param("error_description", "User denied access")
                .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/accounts"))
            .andExpect(flash().attributeExists("error"));
    }

    @Test
    void testOAuth2Flow_UnknownProvider() throws Exception {
        when(tokenService.getProvider("unknown")).thenReturn(null);

        mockMvc.perform(get("/oauth2/authorize/unknown"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/accounts?error=*"));
    }

    @Test
    void testDisconnectOAuth2Account() throws Exception {
        EmailAccount account = new EmailAccount();
        account.setId(1L);
        account.setEmailAddress("test@outlook.com");

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        mockMvc.perform(post("/oauth2/disconnect/1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/accounts"))
            .andExpect(flash().attributeExists("success"));
    }

    @Test
    void testReconnect_NonOAuth2Account_Rejected() throws Exception {
        EmailAccount basicAccount = new EmailAccount();
        basicAccount.setId(1L);
        basicAccount.setAuthType("basic"); // Not OAuth2

        when(accountRepository.findById(1L)).thenReturn(Optional.of(basicAccount));

        mockMvc.perform(get("/oauth2/reconnect/1"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("/accounts?error=*"));
    }
}
