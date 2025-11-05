# Run AI Email Chat on Your Local Machine

The Claude Code remote environment cannot connect to external email servers due to network restrictions.
You must run this application on your local computer.

## Steps to Run Locally

### 1. Clone the Repository to Your Mac

```bash
# On your Mac terminal
cd ~/Projects  # or wherever you keep projects
git clone https://github.com/iv9eni/ai-email-chat.git
cd ai-email-chat
```

### 2. Install Prerequisites

**Install Java 17+:**
```bash
# Check if you have Java
java -version

# If not installed, use Homebrew:
brew install openjdk@17
```

**Install Ollama** (for AI responses):
```bash
# Install Ollama
brew install ollama

# Start Ollama service
brew services start ollama

# Pull a model
ollama pull llama2
```

### 3. Run the Application

```bash
cd ~/Projects/ai-email-chat
./gradlew bootRun
```

The app will start at: **http://localhost:8080**

### 4. Add Your Hotmail Account

1. Open browser: **http://localhost:8080/accounts**
2. Click **"Add Account"**
3. Click **"Outlook"** preset button
4. Fill in:
   - **Email**: ivgeni.darinski@hotmail.com
   - **Username**: ivgeni.darinski@hotmail.com
   - **Password**: kebtxtesmvqlncrh
   - **Leave SSL checked**
5. Click **"Add Account"**

### 5. Verify Connection

After adding the account, check the console logs. You should see:
```
✓ Successfully connected to IMAP server for ivgeni.darinski@hotmail.com
```

If you still see authentication errors:
1. Go to: https://outlook.live.com/mail/0/options/mail/accounts
2. Verify "Let devices and apps use POP" is ON
3. Wait 30 minutes if you just enabled it
4. Create a NEW app password: https://account.microsoft.com/security
5. Update your account with the new password

## Troubleshooting

### Check Ollama is Running
```bash
curl http://localhost:11434/api/tags
```

### Check App Logs
```bash
tail -f logs/ai-email-chat.log
```

### Test Account Connection
Navigate to: http://localhost:8080/diagnostics/test/1
(Replace `1` with your account ID)

## How It Works

1. **Email arrives** with subject starting with `[AI_REQUEST]`
2. **App reads email** via IMAP
3. **Ollama generates AI response** based on email content
4. **App sends reply** via SMTP

## Next Steps

Once running locally, send a test email to yourself:
- Subject: `[AI_REQUEST] Hello AI`
- Body: `Please tell me a joke`

The app will reply within 60 seconds (default poll rate).
