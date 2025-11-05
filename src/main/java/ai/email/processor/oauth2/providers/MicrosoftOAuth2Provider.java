package ai.email.processor.oauth2.providers;

import ai.email.processor.oauth2.OAuth2Provider;
import ai.email.processor.oauth2.OAuth2TokenResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.Base64;

/**
 * Microsoft OAuth2 provider for Outlook/Hotmail accounts
 */
@Component
public class MicrosoftOAuth2Provider implements OAuth2Provider {

    private static final Logger logger = LoggerFactory.getLogger(MicrosoftOAuth2Provider.class);

    private static final String AUTHORIZATION_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
    private static final String GRAPH_API_URL = "https://graph.microsoft.com/v1.0/me";

    // Microsoft scopes for email access
    private static final String[] SCOPES = {
        "https://outlook.office365.com/IMAP.AccessAsUser.All",
        "https://outlook.office365.com/SMTP.Send",
        "offline_access", // For refresh tokens
        "User.Read" // To get user email
    };

    @Value("${app.oauth2.microsoft.client-id}")
    private String clientId;

    @Value("${app.oauth2.microsoft.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getProviderName() {
        return "microsoft";
    }

    @Override
    public String getAuthorizationUrl(String redirectUri, String state) {
        return UriComponentsBuilder.fromHttpUrl(AUTHORIZATION_URL)
                .queryParam("client_id", clientId)
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_mode", "query")
                .queryParam("scope", String.join(" ", SCOPES))
                .queryParam("state", state)
                .build()
                .toUriString();
    }

    @Override
    public OAuth2TokenResponse exchangeCodeForTokens(String code, String redirectUri) {
        logger.info("Exchanging authorization code for tokens");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        body.add("grant_type", "authorization_code");

        return requestTokens(body);
    }

    @Override
    public OAuth2TokenResponse refreshAccessToken(String refreshToken) {
        logger.info("Refreshing Microsoft access token");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);
        body.add("grant_type", "refresh_token");
        body.add("scope", String.join(" ", SCOPES));

        return requestTokens(body);
    }

    @Override
    public String getUserEmail(String accessToken) {
        logger.info("Fetching user email from Microsoft Graph API");

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    GRAPH_API_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());
            String email = jsonNode.get("userPrincipalName").asText();

            logger.info("Retrieved user email: {}", email);
            return email;

        } catch (Exception e) {
            logger.error("Failed to get user email from Microsoft Graph", e);
            throw new RuntimeException("Failed to get user email", e);
        }
    }

    @Override
    public String getDefaultImapHost() {
        return "outlook.office365.com";
    }

    @Override
    public int getDefaultImapPort() {
        return 993;
    }

    @Override
    public String getDefaultSmtpHost() {
        return "smtp-mail.outlook.com";
    }

    @Override
    public int getDefaultSmtpPort() {
        return 587;
    }

    @Override
    public boolean getDefaultUseSSL() {
        return true;
    }

    @Override
    public String[] getRequiredScopes() {
        return SCOPES;
    }

    /**
     * Common method to request tokens from Microsoft
     */
    private OAuth2TokenResponse requestTokens(MultiValueMap<String, String> body) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    TOKEN_URL,
                    request,
                    String.class
            );

            JsonNode jsonNode = objectMapper.readTree(response.getBody());

            String accessToken = jsonNode.get("access_token").asText();
            String refreshToken = jsonNode.has("refresh_token") ?
                    jsonNode.get("refresh_token").asText() : null;
            int expiresIn = jsonNode.get("expires_in").asInt();

            LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(expiresIn);

            OAuth2TokenResponse tokenResponse = new OAuth2TokenResponse(accessToken, refreshToken, expiresAt);
            tokenResponse.setTokenType(jsonNode.get("token_type").asText());
            tokenResponse.setScope(jsonNode.has("scope") ? jsonNode.get("scope").asText() : null);

            logger.info("Successfully obtained Microsoft tokens, expires in {} seconds", expiresIn);
            return tokenResponse;

        } catch (Exception e) {
            logger.error("Failed to request tokens from Microsoft", e);
            throw new RuntimeException("Failed to obtain OAuth2 tokens", e);
        }
    }
}
