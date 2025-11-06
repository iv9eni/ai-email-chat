package ai.email.processor.oauth2;

import ai.email.processor.entity.EmailAccount;

/**
 * Interface for OAuth2 providers (Google, Microsoft, Yahoo, etc.)
 * Each provider implementation handles provider-specific OAuth2 flows
 */
public interface OAuth2Provider {

    /**
     * Get the provider name (e.g., "google", "microsoft", "yahoo")
     */
    String getProviderName();

    /**
     * Build the authorization URL for the OAuth2 flow
     * @param redirectUri The callback URL after authorization
     * @param state A unique state parameter to prevent CSRF attacks
     * @return The complete authorization URL to redirect the user to
     */
    String getAuthorizationUrl(String redirectUri, String state);

    /**
     * Exchange the authorization code for access and refresh tokens
     * @param code The authorization code from the OAuth callback
     * @param redirectUri The same redirect URI used in authorization
     * @return OAuth2TokenResponse containing access token, refresh token, and expiry
     */
    OAuth2TokenResponse exchangeCodeForTokens(String code, String redirectUri);

    /**
     * Refresh an expired access token using the refresh token
     * @param refreshToken The refresh token from the account
     * @return OAuth2TokenResponse with new access token and expiry
     */
    OAuth2TokenResponse refreshAccessToken(String refreshToken);

    /**
     * Get the user's email address using the access token
     * This is used to verify the account and populate the email field
     * @param accessToken The OAuth2 access token
     * @return The user's email address
     */
    String getUserEmail(String accessToken);

    /**
     * Get the default IMAP host for this provider
     */
    String getDefaultImapHost();

    /**
     * Get the default IMAP port for this provider
     */
    int getDefaultImapPort();

    /**
     * Get the default SMTP host for this provider
     */
    String getDefaultSmtpHost();

    /**
     * Get the default SMTP port for this provider
     */
    int getDefaultSmtpPort();

    /**
     * Check if SSL should be used by default
     */
    boolean getDefaultUseSSL();

    /**
     * Get the OAuth2 scopes required for this provider
     * @return Array of scope strings
     */
    String[] getRequiredScopes();
}
