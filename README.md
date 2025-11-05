# AI Email Chat Service

An intelligent email automation service that uses local LLMs (via Ollama) to automatically respond to emails. The service monitors multiple email accounts, processes emails with specific subject filters, maintains conversation context, and generates AI-powered responses.

## Features

- **Multi-Account Support**: Manage multiple email accounts (Gmail, Outlook, Yahoo, etc.) from a single dashboard
- **Smart Email Filtering**: Only processes emails with `[AI_REQUEST]` subject prefix
- **Conversation Memory**: Maintains conversation history with each sender for contextual responses
- **Automatic AI Responses**: Uses Ollama to generate intelligent, context-aware replies
- **Web Dashboard**: Simple UI for managing email accounts and viewing conversations
- **Database Storage**: Persistent storage of conversations and messages

## Architecture

```
┌─────────────┐         ┌──────────────┐         ┌─────────────┐
│   Email     │  IMAP   │   Spring     │         │   Ollama    │
│   Servers   ├────────>│   Boot App   ├────────>│   AI Model  │
│  (Multiple) │         │              │         │             │
└─────────────┘         └───────┬──────┘         └─────────────┘
                                │
                         SMTP   │
                                │
                        ┌───────▼──────┐
                        │   Database   │
                        │  (H2/Postgres)│
                        └──────────────┘
```

## Prerequisites

- Java 21 or higher
- Docker and Docker Compose (for Ollama and PostgreSQL)
- Email account(s) with IMAP/SMTP access enabled
  - For Gmail: Enable "App Passwords" (requires 2FA)
  - For Outlook: May require app-specific password
  - For Yahoo: Generate app password from account security

## Quick Start

### 1. Start Required Services

Start Ollama and PostgreSQL using Docker Compose:

```bash
docker-compose up -d
```

### 2. Pull Ollama Model

Download the AI model (default: llama3.2):

```bash
docker exec -it $(docker ps -qf "name=ollama") ollama pull llama3.2
```

Or use a different model by updating `application.yml` and pulling that model:

```bash
docker exec -it $(docker ps -qf "name=ollama") ollama pull mistral
```

### 3. Build and Run the Application

```bash
./gradlew bootRun
```

The application will start on `http://localhost:8080`

### 4. Configure Email Accounts

1. Open your browser and navigate to `http://localhost:8080`
2. Click "Manage Email Accounts"
3. Click "+ Add Account"
4. Select your email provider preset (Gmail, Outlook, Yahoo) or enter custom settings
5. Fill in your credentials:
   - **Display Name**: A friendly name for the account
   - **Email Address**: Your email address
   - **Username**: Usually your email address
   - **Password**: Your email password or app-specific password
6. Click "Add Account"

### 5. Test the Service

Send an email to one of your configured accounts with:
- **Subject**: `[AI_REQUEST] Your question here`
- **Body**: Your message to the AI

The service will:
1. Detect the email (checks every 60 seconds by default)
2. Process it through Ollama
3. Send an AI-generated response back to the sender

## Configuration

### application.yml

Key configuration options:

```yaml
ai:
  email:
    chat:
      # How often to check for new emails (milliseconds)
      poll-rate: 60000
      # Subject prefix required for processing
      subject-filter: "[AI_REQUEST]"

ollama:
  # Ollama API endpoint
  base-url: http://localhost:11434
  # Model to use for responses
  model: llama3.2

spring:
  datasource:
    # Use H2 for development (default)
    url: jdbc:h2:file:./data/emailchat
    # Or PostgreSQL for production
    # url: jdbc:postgresql://localhost:5432/emailchat
    # username: emailchat
    # password: emailchat_password
```

### Email Provider Settings

#### Gmail
- IMAP: `imap.gmail.com:993`
- SMTP: `smtp.gmail.com:587`
- Enable 2FA and create an App Password

#### Outlook/Office 365
- IMAP: `outlook.office365.com:993`
- SMTP: `smtp.office365.com:587`
- May require app password from account settings

#### Yahoo
- IMAP: `imap.mail.yahoo.com:993`
- SMTP: `smtp.mail.yahoo.com:587`
- Generate app password from account security

## Usage

### Web Interface

The web UI provides:

- **Home**: Overview of configured accounts and system status
- **Accounts**: Add, edit, activate/deactivate, and delete email accounts
- **Conversations**: View conversation history for each account
- **Messages**: Detailed view of all messages in a conversation

### API Endpoints

#### Email Accounts
- `GET /api/accounts` - List all accounts
- `POST /api/accounts` - Add new account
- `PUT /api/accounts/{id}` - Update account
- `DELETE /api/accounts/{id}` - Delete account
- `PATCH /api/accounts/{id}/toggle?active=true` - Toggle account status

#### Conversations
- `GET /api/conversations/account/{accountId}` - List conversations for an account
- `GET /api/conversations/{id}` - Get conversation details
- `GET /api/conversations/{id}/messages` - Get all messages in a conversation

### Database Console

H2 database console is available at `http://localhost:8080/h2-console`

- JDBC URL: `jdbc:h2:file:./data/emailchat`
- Username: `sa`
- Password: `password`

## Development

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Create Executable JAR

```bash
./gradlew bootJar
```

The JAR will be in `build/libs/processor-0.0.1-SNAPSHOT.jar`

### Run with Docker

Build and run the entire stack:

```bash
docker-compose up --build
```

## Project Structure

```
src/main/java/ai/email/processor/
├── ProcessorApplication.java          # Main application class
├── controller/                        # REST API and web controllers
│   ├── EmailAccountController.java
│   ├── ConversationController.java
│   └── WebController.java
├── entity/                            # Database entities
│   ├── EmailAccount.java
│   ├── Conversation.java
│   └── Message.java
├── repository/                        # Data access layer
│   ├── EmailAccountRepository.java
│   ├── ConversationRepository.java
│   └── MessageRepository.java
└── service/                           # Business logic
    ├── EmailAccountService.java       # Account management
    ├── EmailReceiverService.java      # IMAP email polling
    ├── EmailSenderService.java        # SMTP email sending
    └── ConversationService.java       # AI conversation handling

src/main/resources/
├── application.yml                    # Application configuration
└── templates/                         # Thymeleaf HTML templates
    ├── index.html
    ├── accounts.html
    ├── conversations.html
    └── conversation.html
```

## Troubleshooting

### Email Authentication Errors

**Problem**: "Authentication failed" when adding account

**Solutions**:
- Gmail: Enable 2FA and use an App Password instead of your regular password
- Outlook: Try generating an app-specific password
- Yahoo: Generate app password from account security settings
- Check IMAP/SMTP access is enabled in your email provider settings

### Ollama Connection Errors

**Problem**: "Connection refused to localhost:11434"

**Solutions**:
```bash
# Check if Ollama is running
docker ps | grep ollama

# Restart Ollama
docker-compose restart ollama

# Check Ollama logs
docker logs $(docker ps -qf "name=ollama")
```

### Emails Not Being Processed

**Problem**: Emails aren't getting AI responses

**Checklist**:
1. Email account is marked as "Active" in the dashboard
2. Email subject starts with `[AI_REQUEST]`
3. Email is unread when the service checks (every 60 seconds)
4. Check application logs for errors:
   ```bash
   ./gradlew bootRun
   ```

### Database Issues

**Problem**: Cannot connect to database

**Solutions**:
```bash
# For H2 (default):
# Check if data directory exists and has write permissions
mkdir -p ./data

# For PostgreSQL:
# Check if container is running
docker ps | grep postgres

# Restart PostgreSQL
docker-compose restart postgres
```

## Security Considerations

⚠️ **Important**: This is a development/personal use application. For production:

1. **Encrypt Passwords**: Currently passwords are stored in plain text. Implement encryption:
   - Use Spring Security Crypto
   - Consider using a secrets management service

2. **Add Authentication**: The web UI has no authentication. Add:
   - Spring Security
   - User login system
   - OAuth2 for email providers

3. **Use HTTPS**: For production, enable SSL/TLS

4. **Rate Limiting**: Implement rate limiting for API calls

5. **Email Validation**: Add additional validation for email processing

## Future Enhancements

- [ ] OAuth2 authentication for email providers
- [ ] Support for attachments in emails
- [ ] Email template customization
- [ ] Multiple AI model selection per account
- [ ] Scheduled email sending
- [ ] Email classification and routing
- [ ] Metrics and monitoring dashboard
- [ ] Docker image for easy deployment
- [ ] Webhook support for real-time email processing
- [ ] Multi-language support for AI responses

## License

Apache License 2.0

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

For issues, questions, or contributions, please open an issue on GitHub.
