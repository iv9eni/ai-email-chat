package ai.email.processor.config;

import ai.email.processor.oauth2.OAuth2TokenService;
import ai.email.processor.oauth2.providers.MicrosoftOAuth2Provider;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to register OAuth2 providers
 */
@Configuration
public class OAuth2Configuration {

    private final OAuth2TokenService tokenService;
    private final MicrosoftOAuth2Provider microsoftProvider;

    public OAuth2Configuration(OAuth2TokenService tokenService,
                              MicrosoftOAuth2Provider microsoftProvider) {
        this.tokenService = tokenService;
        this.microsoftProvider = microsoftProvider;
    }

    @PostConstruct
    public void registerProviders() {
        // Register Microsoft provider
        tokenService.registerProvider(microsoftProvider);

        // TODO: Register Google and Yahoo providers when implemented
    }
}
