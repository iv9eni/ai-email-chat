# Docker Setup Guide

Run the entire AI Email Chat system with Docker Compose - no need to install Java, Gradle, or Ollama separately!

## Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/iv9eni/ai-email-chat.git
cd ai-email-chat

# 2. Start all services
docker-compose up -d

# 3. Initialize Ollama with AI model (first time only)
./init-ollama.sh

# 4. Open the web interface
open http://localhost:8080
```

That's it! The app is now running at **http://localhost:8080**

## What Gets Started

Docker Compose starts 3 services:

1. **ai-email-app** - Spring Boot application (port 8080)
2. **ai-email-ollama** - Ollama AI service (port 11434)
3. **ai-email-postgres** - PostgreSQL database (port 5432)

All services are connected via a private network and configured to work together.

## Adding Your Email Account

### For Outlook/Hotmail:

1. **Enable IMAP** (REQUIRED FIRST!):
   - Go to: https://outlook.live.com/mail/0/options/mail/accounts
   - Turn ON "Let devices and apps use POP"
   - **Wait 30 minutes** for IMAP to activate

2. **Create App Password**:
   - Go to: https://account.microsoft.com/security
   - Click "Advanced security options"
   - Find "App passwords" → "Create a new app password"
   - Copy the password (remove dashes): `abcd-efgh-ijkl-mnop` → `abcdefghijklmnop`

3. **Add Account in Web UI**:
   - Open: http://localhost:8080/accounts
   - Click "Add Account"
   - Click "Outlook" preset button
   - Fill in:
     - **Email**: your.email@hotmail.com
     - **Username**: your.email@hotmail.com
     - **Password**: abcdefghijklmnop (app password without dashes)
   - Click "Add Account"

### For Gmail:

1. **Enable 2FA**: https://myaccount.google.com/security
2. **Create App Password**: https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (Custom name)"
   - Copy the 16-character password (remove spaces)
3. **Add Account**: Use Gmail preset in http://localhost:8080/accounts

### For Yahoo:

1. **Create App Password**: https://login.yahoo.com/account/security
   - Select "Other App" from dropdown
2. **Add Account**: Use Yahoo preset in http://localhost:8080/accounts

## Testing Your Setup

Send yourself an email:
- **To**: your.configured.email@example.com
- **Subject**: `[AI_REQUEST] Hello AI`
- **Body**: `Tell me a joke about Docker containers`

Within 60 seconds, you should receive an AI-generated reply!

## Common Commands

```bash
# Start all services
docker-compose up -d

# View logs (all services)
docker-compose logs -f

# View logs (app only)
docker-compose logs -f app

# Stop all services
docker-compose down

# Stop and remove all data (including volumes)
docker-compose down -v

# Rebuild app after code changes
docker-compose up -d --build app

# Check service status
docker-compose ps

# Access Ollama CLI
docker exec -it ai-email-ollama ollama run llama2
```

## Configuration

Edit `compose.yaml` to customize:

### Change AI Model

```yaml
# After starting services, exec into Ollama container:
docker exec -it ai-email-ollama ollama pull mistral
docker exec -it ai-email-ollama ollama pull codellama
```

Update your app to use the new model in `application.yml` or via environment variable.

### Change Email Polling Rate

```yaml
environment:
  AI_EMAIL_CHAT_POLL_RATE: 30000  # Check every 30 seconds
```

### Change Subject Filter

```yaml
environment:
  AI_EMAIL_CHAT_SUBJECT_FILTER: '[BOT]'  # Only process emails with [BOT] in subject
```

### Use PostgreSQL Instead of H2

Uncomment these lines in `compose.yaml`:

```yaml
# SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/emailchat
# SPRING_DATASOURCE_USERNAME: emailchat
# SPRING_DATASOURCE_PASSWORD: emailchat_password
# SPRING_JPA_DATABASE_PLATFORM: org.hibernate.dialect.PostgreSQLDialect
```

## Troubleshooting

### Ollama not responding

```bash
# Check Ollama is running
docker-compose ps ollama

# Check Ollama logs
docker-compose logs ollama

# Verify model is pulled
docker exec ai-email-ollama ollama list

# Pull model if missing
docker exec ai-email-ollama ollama pull llama2
```

### Email authentication fails

Check logs for details:
```bash
docker-compose logs -f app | grep -i auth
```

Common issues:
- ✗ App password has dashes/spaces (remove them)
- ✗ IMAP not enabled (wait 30 minutes after enabling)
- ✗ Wrong username (use full email address)
- ✗ Using regular password instead of app password

### App won't start

```bash
# Check app logs
docker-compose logs app

# Rebuild from scratch
docker-compose down -v
docker-compose up -d --build
./init-ollama.sh
```

### Network/Proxy Issues

If running in a restricted environment (like Claude Code remote), Docker containers may be blocked from reaching external email servers.

**Solution**: Run on your local machine where you have normal internet access.

The `compose.yaml` already includes proxy bypass configuration for:
- outlook.office365.com
- smtp-mail.outlook.com
- imap.gmail.com
- smtp.gmail.com
- imap.mail.yahoo.com
- smtp.mail.yahoo.com

## Development Workflow

### Make Code Changes

1. Edit your source code
2. Rebuild the app:
   ```bash
   docker-compose up -d --build app
   ```

### View Real-time Logs

```bash
# All services
docker-compose logs -f

# Just the app
docker-compose logs -f app

# Just email-related logs
docker-compose logs -f app | grep -i email
```

### Access Database

**H2 Console** (default):
- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:file:./data/emailchat`
- Username: `sa`
- Password: (leave empty)

**PostgreSQL** (if enabled):
```bash
docker exec -it ai-email-postgres psql -U emailchat -d emailchat
```

### Clean Slate

```bash
# Stop everything and delete all data
docker-compose down -v

# Delete local H2 database
rm -rf data/

# Start fresh
docker-compose up -d
./init-ollama.sh
```

## Architecture

```
┌─────────────────┐
│   Your Email    │
│    Provider     │
│ (Gmail/Outlook) │
└────────┬────────┘
         │ IMAP/SMTP
         │
┌────────▼─────────┐      ┌──────────────┐
│  ai-email-app    │◄────►│   Ollama     │
│  (Spring Boot)   │      │ (AI Service) │
│   Port 8080      │      │ Port 11434   │
└────────┬─────────┘      └──────────────┘
         │
         │
┌────────▼─────────┐
│   PostgreSQL     │
│   (Database)     │
│   Port 5432      │
└──────────────────┘
```

## Next Steps

- Visit http://localhost:8080/accounts to add email accounts
- Send test email with subject `[AI_REQUEST] Hello`
- Check logs: `docker-compose logs -f app`
- Monitor health: http://localhost:8080/actuator/health
- View all accounts: http://localhost:8080/accounts
- Edit existing accounts: Click "Edit" button on any account card

## Resources

- [Ollama Models](https://ollama.ai/library)
- [Spring Boot Actuator](http://localhost:8080/actuator)
- [Application Logs](./logs/ai-email-chat.log)
