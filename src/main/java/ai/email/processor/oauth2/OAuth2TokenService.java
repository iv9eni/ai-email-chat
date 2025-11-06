package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing OAuth2 tokens across all providers
 */
@Service
public class OAuth2TokenService {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2TokenService.class);

    private final EmailAccountRepository accountRepository;
    private final Map<String, OAuth2Provider> providers = new HashMap<>();

    public OAuth2TokenService(EmailAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Register an OAuth2 provider
     * @param provider The OAuth2 provider implementation
     */
    public void registerProvider(OAuth2Provider provider) {
        providers.put(provider.getProviderName(), provider);
        logger.info("Registered OAuth2 provider: {}", provider.getProviderName());
    }

    /**
     * Get a registered OAuth2 provider by name
     * @param providerName The provider name (e.g., "google", "microsoft", "yahoo")
     * @return The OAuth2Provider or null if not found
     */
    public OAuth2Provider getProvider(String providerName) {
        return providers.get(providerName);
    }

    /**
     * Get all registered provider names
     */
    public String[] getAvailableProviders() {
        return providers.keySet().toArray(new String[0]);
    }

    /**
     * Store OAuth2 tokens in an email account
     * @param account The email account to update
     * @param tokenResponse The token response from the provider
     */
    @Transactional
    public void storeTokens(EmailAccount account, OAuth2TokenResponse tokenResponse) {
        account.setAccessToken(tokenResponse.getAccessToken());
        account.setRefreshToken(tokenResponse.getRefreshToken());
        account.setTokenExpiresAt(tokenResponse.getExpiresAt());
        account.setAuthType("oauth2");
        accountRepository.save(account);
        logger.info("Stored OAuth2 tokens for account: {}", account.getEmailAddress());
    }

    /**
     * Refresh the access token if it's expired or about to expire
     * @param account The email account
     * @return true if token was refreshed, false if refresh wasn't needed or failed
     */
    @Transactional
    public boolean refreshTokenIfNeeded(EmailAccount account) {
        if (!account.isOAuth2()) {
            return false; // Not an OAuth2 account
        }

        // Check if token is expired or will expire in the next 5 minutes
        if (account.getTokenExpiresAt() != null &&
            LocalDateTime.now().plusMinutes(5).isAfter(account.getTokenExpiresAt())) {

            logger.info("Access token expired or expiring soon for account: {}, attempting refresh",
                       account.getEmailAddress());

            return refreshToken(account);
        }

        return false; // Token is still valid
    }

    /**
     * Force refresh the access token
     * @param account The email account
     * @return true if refresh succeeded, false otherwise
     */
    @Transactional
    public boolean refreshToken(EmailAccount account) {
        if (!account.isOAuth2()) {
            logger.error("Cannot refresh token for non-OAuth2 account: {}", account.getEmailAddress());
            return false;
        }

        OAuth2Provider provider = getProvider(account.getProvider());
        if (provider == null) {
            logger.error("OAuth2 provider not found: {}", account.getProvider());
            return false;
        }

        try {
            OAuth2TokenResponse tokenResponse = provider.refreshAccessToken(account.getRefreshToken());

            account.setAccessToken(tokenResponse.getAccessToken());
            account.setTokenExpiresAt(tokenResponse.getExpiresAt());

            // Some providers may return a new refresh token
            if (tokenResponse.getRefreshToken() != null) {
                account.setRefreshToken(tokenResponse.getRefreshToken());
            }

            accountRepository.save(account);
            logger.info("Successfully refreshed access token for account: {}", account.getEmailAddress());
            return true;

        } catch (Exception e) {
            logger.error("Failed to refresh access token for account: {}", account.getEmailAddress(), e);
            return false;
        }
    }

    /**
     * Get a valid access token for an account, refreshing if necessary
     * @param account The email account
     * @return The valid access token, or null if unable to get one
     */
    @Transactional
    public String getValidAccessToken(EmailAccount account) {
        if (!account.isOAuth2()) {
            return null;
        }

        // Refresh if needed
        refreshTokenIfNeeded(account);

        // Reload account from database to get updated token
        EmailAccount reloadedAccount = accountRepository.findById(account.getId()).orElse(null);
        if (reloadedAccount == null) {
            return null;
        }

        return reloadedAccount.getAccessToken();
    }

    /**
     * Revoke OAuth2 access for an account (clear tokens)
     * @param account The email account
     */
    @Transactional
    public void revokeAccess(EmailAccount account) {
        account.setAccessToken(null);
        account.setRefreshToken(null);
        account.setTokenExpiresAt(null);
        account.setAuthType("basic");
        accountRepository.save(account);
        logger.info("Revoked OAuth2 access for account: {}", account.getEmailAddress());
    }
}
