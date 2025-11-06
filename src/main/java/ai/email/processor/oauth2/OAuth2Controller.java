package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

/**
 * Controller for handling OAuth2 authentication flows
 */
@Controller
@RequestMapping("/oauth2")
public class OAuth2Controller {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Controller.class);

    private final OAuth2TokenService tokenService;
    private final EmailAccountRepository accountRepository;

    @Value("${app.oauth2.redirect-base-url:http://localhost:8080}")
    private String redirectBaseUrl;

    public OAuth2Controller(OAuth2TokenService tokenService, EmailAccountRepository accountRepository) {
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
    }

    /**
     * Initiate OAuth2 flow for a provider
     * This creates a new account or re-authenticates an existing one
     */
    @GetMapping("/authorize/{provider}")
    public String authorize(@PathVariable String provider,
                           @RequestParam(required = false) Long accountId,
                           HttpSession session) {

        logger.info("Starting OAuth2 authorization for provider: {}", provider);

        OAuth2Provider oauth2Provider = tokenService.getProvider(provider);
        if (oauth2Provider == null) {
            logger.error("Unknown OAuth2 provider: {}", provider);
            return "redirect:/accounts?error=unknown_provider";
        }

        // Generate CSRF protection state
        String state = UUID.randomUUID().toString();
        session.setAttribute("oauth2_state", state);
        session.setAttribute("oauth2_provider", provider);

        // Store account ID if re-authenticating existing account
        if (accountId != null) {
            session.setAttribute("oauth2_account_id", accountId);
        }

        // Build redirect URI
        String redirectUri = redirectBaseUrl + "/oauth2/callback/" + provider;

        // Get authorization URL from provider
        String authUrl = oauth2Provider.getAuthorizationUrl(redirectUri, state);

        logger.info("Redirecting to OAuth2 provider authorization URL");
        return "redirect:" + authUrl;
    }

    /**
     * Handle OAuth2 callback from provider
     */
    @GetMapping("/callback/{provider}")
    public String callback(@PathVariable String provider,
                          @RequestParam(required = false) String code,
                          @RequestParam(required = false) String state,
                          @RequestParam(required = false) String error,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {

        logger.info("Received OAuth2 callback for provider: {}", provider);

        // Check for errors from provider
        if (error != null) {
            logger.error("OAuth2 error from provider: {}", error);
            redirectAttributes.addFlashAttribute("error", "OAuth2 authorization failed: " + error);
            return "redirect:/accounts";
        }

        // Validate state for CSRF protection
        String expectedState = (String) session.getAttribute("oauth2_state");
        String expectedProvider = (String) session.getAttribute("oauth2_provider");

        if (expectedState == null || !expectedState.equals(state)) {
            logger.error("OAuth2 state mismatch - possible CSRF attack");
            redirectAttributes.addFlashAttribute("error", "Security validation failed. Please try again.");
            return "redirect:/accounts";
        }

        if (!provider.equals(expectedProvider)) {
            logger.error("OAuth2 provider mismatch - expected: {}, got: {}", expectedProvider, provider);
            redirectAttributes.addFlashAttribute("error", "Provider mismatch. Please try again.");
            return "redirect:/accounts";
        }

        // Get provider implementation
        OAuth2Provider oauth2Provider = tokenService.getProvider(provider);
        if (oauth2Provider == null) {
            logger.error("Unknown OAuth2 provider: {}", provider);
            redirectAttributes.addFlashAttribute("error", "Unknown provider");
            return "redirect:/accounts";
        }

        try {
            // Exchange code for tokens
            String redirectUri = redirectBaseUrl + "/oauth2/callback/" + provider;
            OAuth2TokenResponse tokenResponse = oauth2Provider.exchangeCodeForTokens(code, redirectUri);

            // Get user's email address
            String emailAddress = oauth2Provider.getUserEmail(tokenResponse.getAccessToken());

            // Check if re-authenticating existing account
            Long accountId = (Long) session.getAttribute("oauth2_account_id");
            EmailAccount account;

            if (accountId != null) {
                // Re-authenticating existing account
                account = accountRepository.findById(accountId).orElse(null);
                if (account == null) {
                    logger.error("Account not found for re-authentication: {}", accountId);
                    redirectAttributes.addFlashAttribute("error", "Account not found");
                    return "redirect:/accounts";
                }
                logger.info("Re-authenticating existing account: {}", account.getEmailAddress());
            } else {
                // Creating new account
                account = new EmailAccount();
                account.setEmailAddress(emailAddress);
                account.setDisplayName(emailAddress);
                account.setUsername(emailAddress);
                account.setProvider(provider);

                // Set provider-specific defaults
                account.setImapHost(oauth2Provider.getDefaultImapHost());
                account.setImapPort(oauth2Provider.getDefaultImapPort());
                account.setSmtpHost(oauth2Provider.getDefaultSmtpHost());
                account.setSmtpPort(oauth2Provider.getDefaultSmtpPort());
                account.setUseSSL(oauth2Provider.getDefaultUseSSL());

                logger.info("Creating new OAuth2 account: {}", emailAddress);
            }

            // Store tokens
            tokenService.storeTokens(account, tokenResponse);

            // Clear session attributes
            session.removeAttribute("oauth2_state");
            session.removeAttribute("oauth2_provider");
            session.removeAttribute("oauth2_account_id");

            redirectAttributes.addFlashAttribute("success",
                "Successfully connected " + emailAddress + " via " + provider);
            return "redirect:/accounts";

        } catch (Exception e) {
            logger.error("Error during OAuth2 callback processing", e);
            redirectAttributes.addFlashAttribute("error",
                "Failed to connect account: " + e.getMessage());
            return "redirect:/accounts";
        }
    }

    /**
     * Disconnect OAuth2 account (revoke tokens)
     */
    @PostMapping("/disconnect/{accountId}")
    public String disconnect(@PathVariable Long accountId, RedirectAttributes redirectAttributes) {
        EmailAccount account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            redirectAttributes.addFlashAttribute("error", "Account not found");
            return "redirect:/accounts";
        }

        tokenService.revokeAccess(account);
        redirectAttributes.addFlashAttribute("success",
            "Disconnected OAuth2 access for " + account.getEmailAddress());
        return "redirect:/accounts";
    }

    /**
     * Re-authenticate an OAuth2 account
     */
    @GetMapping("/reconnect/{accountId}")
    public String reconnect(@PathVariable Long accountId, HttpSession session) {
        EmailAccount account = accountRepository.findById(accountId).orElse(null);
        if (account == null || !account.isOAuth2()) {
            return "redirect:/accounts?error=invalid_account";
        }

        // Redirect to authorize endpoint with account ID
        return "redirect:/oauth2/authorize/" + account.getProvider() + "?accountId=" + accountId;
    }
}
