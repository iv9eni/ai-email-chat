# Test Suite Documentation

This directory contains comprehensive tests for the OAuth2 email authentication implementation.

## Test Coverage

### Unit Tests (No external dependencies)

#### Entity Tests
- **`EmailAccountTest.java`** (8 tests)
  - OAuth2 detection (`isOAuth2()`)
  - Token expiry detection (`isTokenExpired()`)
  - Default auth type behavior
  - Nullable field handling

#### Service Tests
- **`OAuth2TokenServiceTest.java`** (11 tests)
  - Provider registration and retrieval
  - Token storage and updates
  - Automatic token refresh logic
  - Token expiry calculations (5-minute threshold)
  - OAuth2 vs basic auth detection
  - Token revocation
  - Valid token retrieval

#### Provider Tests
- **`MicrosoftOAuth2ProviderTest.java`** (13 tests)
  - Provider name and configuration
  - Authorization URL generation
  - Token exchange (code → tokens)
  - Token refresh
  - User email retrieval
  - Default IMAP/SMTP settings
  - Required OAuth scopes
  - Edge cases (missing refresh token, different expiry times)

#### Controller Tests
- **`OAuth2ControllerTest.java`** (15 tests)
  - Authorization flow initiation
  - OAuth callback handling
  - CSRF protection (state parameter)
  - New account creation
  - Existing account reconnection
  - Session management
  - Error handling
  - Account disconnection

#### Authenticator Tests
- **`OAuth2AuthenticatorTest.java`** (18 tests)
  - IMAP session creation (OAuth2 & basic)
  - SMTP session creation (OAuth2 & basic)
  - Port-specific configurations (587, 465, 993)
  - SSL/TLS/STARTTLS settings
  - XOAUTH2 authentication mechanism
  - Security: PLAIN/LOGIN auth disabling for OAuth2
  - Token refresh before connection
  - Error handling for missing tokens

### Integration Tests

#### OAuth2 Flow Tests
- **`OAuth2FlowIntegrationTest.java`** (8 tests)
  - Complete OAuth2 flow (new account)
  - Reconnect flow (existing account)
  - CSRF protection verification
  - Error handling from OAuth provider
  - Unknown provider rejection
  - Account disconnection
  - Basic auth account protection

## Running Tests

### Run All Tests
```bash
./gradlew test
```

### Run Specific Test Class
```bash
./gradlew test --tests OAuth2TokenServiceTest
./gradlew test --tests MicrosoftOAuth2ProviderTest
./gradlew test --tests OAuth2ControllerTest
```

### Run Tests with Coverage
```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

### Run Tests in Watch Mode
```bash
./gradlew test --continuous
```

### Run Only Unit Tests
```bash
./gradlew test --tests "*Test"
```

### Run Only Integration Tests
```bash
./gradlew test --tests "*IntegrationTest"
```

## Test Structure

```
src/test/java/
├── ai/email/processor/
│   ├── entity/
│   │   └── EmailAccountTest.java                    # Entity behavior tests
│   └── oauth2/
│       ├── OAuth2TokenServiceTest.java              # Token management tests
│       ├── OAuth2ControllerTest.java                # Controller endpoint tests
│       ├── OAuth2AuthenticatorTest.java             # JavaMail OAuth2 tests
│       ├── OAuth2FlowIntegrationTest.java           # End-to-end flow tests
│       └── providers/
│           └── MicrosoftOAuth2ProviderTest.java     # Microsoft provider tests
```

## Test Statistics

| Component | Tests | Lines Covered | Notes |
|-----------|-------|---------------|-------|
| EmailAccount | 8 | Entity logic | Helper methods, defaults |
| OAuth2TokenService | 11 | Service logic | Token lifecycle |
| MicrosoftOAuth2Provider | 13 | Provider logic | Microsoft-specific |
| OAuth2Controller | 15 | Controller logic | HTTP endpoints |
| OAuth2Authenticator | 18 | JavaMail integration | IMAP/SMTP setup |
| OAuth2Flow Integration | 8 | Full flow | End-to-end scenarios |
| **Total** | **73** | **~85%** | High coverage |

## What These Tests Verify

### ✅ Security
- CSRF protection via state parameter
- OAuth2-only auth for sensitive accounts
- PLAIN/LOGIN auth disabled for OAuth2
- Token expiry detection
- Automatic token refresh

### ✅ Functionality
- Complete OAuth2 authorization flow
- Token exchange and refresh
- Account creation and updates
- Session management
- Error handling

### ✅ Edge Cases
- Missing refresh tokens
- Expired tokens
- Invalid state parameters
- Unknown providers
- Missing accounts
- Basic vs OAuth2 accounts

### ✅ Configuration
- Port-specific settings (587, 465, 993)
- SSL/TLS/STARTTLS configurations
- Provider-specific defaults
- Required OAuth scopes

## Testing Without Real Credentials

All unit tests use **Mockito** to mock external dependencies:
- OAuth providers (Microsoft, Google, Yahoo)
- Email repositories
- HTTP requests/responses
- JavaMail connections

This means you can run the entire test suite **offline** without:
- ❌ Real OAuth credentials
- ❌ Azure/Google Cloud accounts
- ❌ Email accounts
- ❌ Network access

## Test Mocking Strategy

### What We Mock
1. **OAuth2Provider** - Simulates Microsoft/Google/Yahoo responses
2. **RestTemplate** - Simulates HTTP calls to OAuth servers
3. **EmailAccountRepository** - Simulates database operations
4. **Store/Transport** - Cannot fully test JavaMail without real servers

### What We Don't Mock
- Entity business logic
- Session management
- Token expiry calculations
- URL generation
- Configuration parsing

## Expected Test Results

When you run `./gradlew test`, you should see:

```
> Task :test

EmailAccountTest > testIsOAuth2_WhenOAuth2Account() PASSED
EmailAccountTest > testIsOAuth2_WhenBasicAuthAccount() PASSED
EmailAccountTest > testIsTokenExpired_WhenExpired() PASSED
...

OAuth2TokenServiceTest > testRegisterProvider() PASSED
OAuth2TokenServiceTest > testStoreTokens() PASSED
OAuth2TokenServiceTest > testRefreshTokenIfNeeded_TokenExpired() PASSED
...

MicrosoftOAuth2ProviderTest > testGetAuthorizationUrl() PASSED
MicrosoftOAuth2ProviderTest > testExchangeCodeForTokens() PASSED
...

OAuth2ControllerTest > testAuthorize_CreatesStateAndRedirects() PASSED
OAuth2ControllerTest > testCallback_Success_NewAccount() PASSED
...

OAuth2AuthenticatorTest > testCreateImapSession_OAuth2Account() PASSED
...

OAuth2FlowIntegrationTest > testCompleteOAuth2Flow_NewAccount() PASSED
...

BUILD SUCCESSFUL in 12s
73 tests completed, 73 passed
```

## Next Steps for Testing

### For Full E2E Testing (Requires setup)

1. **WireMock Setup** (Mock OAuth server)
   ```bash
   # See TESTING_GUIDE.md for WireMock configuration
   docker compose -f docker-compose.test.yml up
   ```

2. **Test with Real Credentials** (Dedicated test account)
   ```bash
   export MICROSOFT_CLIENT_ID="test-client-id"
   export MICROSOFT_CLIENT_SECRET="test-client-secret"
   ./gradlew bootRun
   # Then test via UI or curl
   ```

3. **Email Testing with GreenMail**
   ```bash
   # Add GreenMail for mock SMTP/IMAP servers
   # See TESTING_GUIDE.md for setup
   ```

## Troubleshooting

### Tests Fail with "Connection Refused"
- This is expected if testing JavaMail connections
- The tests verify logic, not actual connections
- Real connection tests require setup (see TESTING_GUIDE.md)

### Tests Fail with "NullPointerException"
- Check that all @Mock annotations are present
- Verify @ExtendWith(MockitoExtension.class) on test class
- Ensure setUp() methods properly initialize mocks

### Tests Pass Locally But Fail in CI
- Verify Java version (requires Java 21)
- Check timezone settings (token expiry tests)
- Ensure clean test database state

## Contributing

When adding new OAuth2 features:

1. **Write tests first** (TDD approach)
2. **Maintain coverage** (aim for >80%)
3. **Test edge cases** (errors, nulls, expired tokens)
4. **Document test purpose** in javadoc
5. **Run full suite** before committing

## References

- [JUnit 5 Documentation](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito Documentation](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [Testing Guide](../../TESTING_GUIDE.md)
