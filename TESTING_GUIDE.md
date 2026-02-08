# Testing Guide

This guide explains how to test the OAuth2 implementation and enable better development/testing workflows.

## What I Can Test (As Claude)

### ✅ Currently Possible

1. **Static Analysis**
   - Code syntax and type checking
   - Configuration validation
   - API endpoint verification
   - Database schema analysis

2. **Unit Tests**
   - Test individual components in isolation
   - Mock external dependencies
   - Verify business logic
   - Test edge cases and error handling

3. **Code Review**
   - Identify logical errors
   - Check for security issues
   - Verify best practices
   - Ensure proper error handling

### ❌ Currently Not Possible

1. **Live OAuth2 Flow**
   - Cannot access Microsoft/Google/Yahoo OAuth servers
   - Cannot create real OAuth apps in Azure/GCP
   - Network restrictions prevent external API calls

2. **Real Email Testing**
   - Cannot connect to real IMAP/SMTP servers
   - Cannot send/receive actual emails
   - Cannot verify end-to-end email flows

3. **Full Integration Tests**
   - Cannot run the complete application stack
   - Gradle dependency downloads fail (network restrictions)
   - Docker networking limitations

## Testing Strategies

### 1. Unit Tests (Recommended - I can write these!)

Unit tests verify individual components in isolation using mocks.

**Already Created:**
- ✅ `OAuth2TokenServiceTest.java` - Tests token management
- ✅ `EmailAccountTest.java` - Tests entity behavior

**Additional Tests to Create:**

```bash
# Run unit tests
./gradlew test

# Run specific test class
./gradlew test --tests OAuth2TokenServiceTest

# Run tests with coverage
./gradlew test jacocoTestReport
```

**What to test:**
- Token refresh logic
- Expiry calculations
- Provider registration
- OAuth2 vs basic auth detection
- Token storage and retrieval

### 2. Integration Tests with Mocks

Test components together with mocked external services.

**Example: Mock OAuth2 Provider**

```java
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuth2Provider microsoftProvider;

    @Test
    void testOAuth2AuthorizationFlow() throws Exception {
        // Mock the provider
        when(microsoftProvider.getAuthorizationUrl(any(), any()))
            .thenReturn("https://mock-auth-url");

        // Test the authorization endpoint
        mockMvc.perform(get("/oauth2/authorize/microsoft"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("https://mock-auth-url*"));
    }
}
```

### 3. Mock Email Servers

Use mock SMTP/IMAP servers for testing email functionality.

**Recommended Tools:**

#### GreenMail (Java)
```java
@SpringBootTest
class EmailServiceIntegrationTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP_IMAP);

    @Test
    void testSendAndReceiveEmail() {
        // GreenMail provides mock SMTP/IMAP servers
        // Test real email sending/receiving locally
    }
}
```

#### MailHog (Docker)
```yaml
# docker-compose.test.yml
services:
  mailhog:
    image: mailhog/mailhog
    ports:
      - "1025:1025"  # SMTP
      - "8025:8025"  # Web UI
```

### 4. WireMock for OAuth2 Endpoints

Mock external OAuth2 providers for integration testing.

**Example Setup:**

```java
@SpringBootTest
@AutoConfigureMockMvc
class OAuth2FlowTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().port(8089))
        .build();

    @Test
    void testTokenExchange() {
        // Mock Microsoft token endpoint
        wireMock.stubFor(post("/oauth2/v2.0/token")
            .willReturn(okJson("{\"access_token\":\"mock-token\"}")));

        // Test token exchange
        // ...
    }
}
```

## What You Can Provide to Enable Better Testing

### Option 1: Test Credentials (Recommended for Quick Testing)

**Create a dedicated test Microsoft account:**

1. Create a new Outlook.com account specifically for testing
2. Register it in Azure Portal as described in OAUTH2_SETUP.md
3. Provide me with:
   ```
   MICROSOFT_CLIENT_ID=...
   MICROSOFT_CLIENT_SECRET=...
   TEST_EMAIL=test@outlook.com
   ```

**⚠️ Security Note:**
- Use a throwaway account, not a real one
- Never share real credentials
- Revoke access after testing

### Option 2: Local Development Environment (Best)

Set up a complete local environment where I can run tests:

```bash
# 1. Ensure Docker is running
docker compose up -d

# 2. Set environment variables
export MICROSOFT_CLIENT_ID="your-test-client-id"
export MICROSOFT_CLIENT_SECRET="your-test-client-secret"

# 3. Run the application
./gradlew bootRun

# 4. Run tests
./gradlew test
```

Then I can:
- Run integration tests
- Test API endpoints with curl
- Check logs for errors
- Verify database state
- Test the full OAuth flow

### Option 3: Mock Server Setup (Development)

Set up WireMock to simulate OAuth2 providers:

```bash
# docker-compose.test.yml
services:
  wiremock:
    image: wiremock/wiremock:latest
    ports:
      - "8089:8080"
    volumes:
      - ./test/wiremock:/home/wiremock
```

Then I can test without real credentials.

## Comprehensive Test Suite

Let me create a complete test suite for you:

### Unit Tests
- ✅ `OAuth2TokenServiceTest` - Token management
- ✅ `EmailAccountTest` - Entity behavior
- 🚧 `MicrosoftOAuth2ProviderTest` - Provider implementation
- 🚧 `OAuth2ControllerTest` - Controller endpoints
- 🚧 `OAuth2AuthenticatorTest` - JavaMail integration

### Integration Tests
- 🚧 `OAuth2FlowIntegrationTest` - Complete OAuth flow
- 🚧 `EmailServiceOAuth2Test` - Email with OAuth2
- 🚧 `TokenRefreshIntegrationTest` - Automatic refresh

### E2E Tests (Requires Environment)
- 🚧 `MicrosoftOAuth2E2ETest` - Real Microsoft OAuth
- 🚧 `EmailRoundTripTest` - Send and receive emails

## Manual Testing Checklist

When you have the environment running:

### 1. OAuth2 Authorization
- [ ] Click "Connect Microsoft" button
- [ ] Redirects to Microsoft login
- [ ] Can sign in successfully
- [ ] Redirected back to /accounts
- [ ] Account appears with OAuth2 badge

### 2. Token Management
- [ ] Access token stored in database
- [ ] Refresh token stored
- [ ] Expiry timestamp correct
- [ ] Token automatically refreshes before expiry

### 3. Email Operations
- [ ] Can receive emails via OAuth2 IMAP
- [ ] Can send emails via OAuth2 SMTP
- [ ] Automatic token refresh works
- [ ] Error handling for expired tokens

### 4. UI Features
- [ ] OAuth2 status badge shows correctly
- [ ] Token expiry indicator works
- [ ] Reconnect button appears for OAuth2 accounts
- [ ] Reconnect flow works

### 5. Error Scenarios
- [ ] Invalid client ID shows error
- [ ] Invalid client secret shows error
- [ ] Expired token auto-refreshes
- [ ] Network error handling
- [ ] User denies permission

## Recommended Testing Tools

### For Unit Testing
```bash
# Add to build.gradle.kts
testImplementation("org.mockito:mockito-core:5.8.0")
testImplementation("org.mockito:mockito-junit-jupiter:5.8.0")
```

### For Integration Testing
```bash
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("com.icegreen:greenmail-junit5:2.0.0")
testImplementation("org.wiremock:wiremock:3.3.1")
```

### For E2E Testing
```bash
testImplementation("org.testcontainers:testcontainers:1.19.3")
testImplementation("org.testcontainers:junit-jupiter:1.19.3")
testImplementation("org.testcontainers:postgresql:1.19.3")
```

## Current Test Status

```
✅ Unit Tests Created:
   - OAuth2TokenServiceTest (11 test cases)
   - EmailAccountTest (8 test cases)

🚧 Integration Tests Needed:
   - OAuth2 flow end-to-end
   - Email send/receive with OAuth2
   - Token refresh scenarios

❌ Cannot Test Without Environment:
   - Real Microsoft OAuth flow
   - Actual email IMAP/SMTP connections
   - Full application integration
```

## How I Can Help

### What I Can Do Right Now:
1. ✅ Write more unit tests for all components
2. ✅ Create integration test scaffolding
3. ✅ Set up WireMock configurations
4. ✅ Write test documentation
5. ✅ Review and fix code based on test results

### What I Need to Test Fully:
1. ❌ Access to running application
2. ❌ Test credentials or mock OAuth server
3. ❌ Network access to external services
4. ❌ Ability to run Gradle builds
5. ❌ Docker environment with networking

## Next Steps

### Immediate (I can do now):
1. Create more unit tests
2. Set up integration test framework
3. Create WireMock stubs for OAuth providers
4. Write test documentation

### When You're Ready:
1. Provide test environment access
2. Share test credentials (dedicated account)
3. Run the tests and share results
4. Iterate based on findings

## Running Tests

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html

# Run only unit tests
./gradlew test --tests "*Test"

# Run only integration tests
./gradlew test --tests "*IntegrationTest"

# Run specific test
./gradlew test --tests OAuth2TokenServiceTest

# Run tests in continuous mode
./gradlew test --continuous
```

## Questions?

Let me know:
1. Do you want me to create more unit tests?
2. Should I set up WireMock for OAuth mocking?
3. Do you have a test environment I can access?
4. Would you like me to create integration test scaffolding?

I'm ready to write comprehensive tests - just let me know what testing approach works best for your workflow!
