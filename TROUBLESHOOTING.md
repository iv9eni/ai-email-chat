# Troubleshooting Guide

## Common Issues and Solutions

### 1. IMAP/SMTP Authentication Failures

#### Error: "AUTHENTICATE failed" or "Authentication Failed"

**Cause:** Your email provider is rejecting your credentials.

**Solutions:**

##### For Gmail:
1. Enable 2-Factor Authentication on your Google account
2. Generate an App Password:
   - Go to: https://myaccount.google.com/apppasswords
   - Select "Mail" and "Other (Custom name)"
   - Copy the 16-character password (spaces optional)
   - Use this as your password in the app
3. Make sure "Less secure app access" is NOT needed (app passwords work with secure access)

##### For Outlook/Hotmail:
1. **Enable IMAP first** (critical step):
   - Go to: https://outlook.live.com/mail/0/options/mail/accounts
   - Click "Sync email"
   - Turn ON "Let devices and apps use POP"
   - Wait 5-10 minutes for it to activate

2. Generate an App Password:
   - Go to: https://account.microsoft.com/security
   - Click "Advanced security options"
   - Under "App passwords", click "Create a new app password"
   - Copy the password shown (format: `xxxx-xxxx-xxxx-xxxx`)
   - Use this as your password

3. Use these settings:
   - **Username:** Your full email address (e.g., `user@hotmail.com`)
   - **Password:** The app password (NOT your regular password)
   - **IMAP Host:** `outlook.office365.com`
   - **IMAP Port:** `993`
   - **SMTP Host:** `smtp.office365.com`
   - **SMTP Port:** `587`
   - **Use SSL:** ✓ Checked (the app handles STARTTLS automatically for port 587)

##### For Yahoo:
1. Generate an App Password:
   - Go to: https://login.yahoo.com/account/security
   - Click "Generate app password"
   - Select "Other App" and name it
   - Copy the password shown
   - Use this as your password

2. Use these settings:
   - **IMAP Host:** `imap.mail.yahoo.com`
   - **IMAP Port:** `993`
   - **SMTP Host:** `smtp.mail.yahoo.com`
   - **SMTP Port:** `587`
   - **Use SSL:** ✓ Checked

---

### 2. SMTP Connection Failures

#### Error: "Could not connect to SMTP host"

**Possible Causes:**
1. Wrong SMTP host or port
2. Firewall blocking outgoing connections
3. SSL/TLS configuration mismatch

**Solutions:**

1. **Verify Port Configuration:**
   - Port 587: Uses STARTTLS (most common)
   - Port 465: Uses SSL/TLS
   - Port 25: Usually blocked by ISPs

2. **Check Firewall:**
   ```bash
   # Test if port is accessible
   telnet smtp.office365.com 587
   # or
   nc -zv smtp.office365.com 587
   ```

3. **Try Alternative Ports:**
   - If 587 fails, try 465 with SSL enabled
   - For Outlook: 587 is preferred

---

### 3. Emails Not Being Processed

#### Issue: Emails are received but not triggering AI responses

**Check the following:**

1. **Subject Line:**
   - Must start with exactly `[AI_REQUEST]`
   - Examples:
     - ✓ `[AI_REQUEST] Hello AI`
     - ✓ `[AI_REQUEST]What is the weather?`
     - ✗ `Re: [AI_REQUEST] Follow up`
     - ✗ `AI_REQUEST without brackets`

2. **Email Must Be Unread:**
   - The service only processes unread emails
   - If you mark as read manually, it won't process

3. **Account Must Be Active:**
   - Check in the dashboard that account is marked "Active"
   - Toggle off and on if needed

4. **Check Logs:**
   ```bash
   tail -f logs/ai-email-chat.log
   ```
   Look for:
   - `=== Starting email check cycle ===`
   - `Found X unread messages`
   - `⊗ Skipping message` - means subject didn't match

---

### 4. Ollama Not Responding

#### Error: "Failed to generate AI response from Ollama"

**Solutions:**

1. **Check Ollama is Running:**
   ```bash
   docker ps | grep ollama
   ```
   Should show a running container

2. **Start Ollama if Needed:**
   ```bash
   docker-compose up -d ollama
   ```

3. **Verify Model is Downloaded:**
   ```bash
   # List available models
   docker exec -it $(docker ps -qf "name=ollama") ollama list

   # Pull the model if needed
   docker exec -it $(docker ps -qf "name=ollama") ollama pull llama3.2
   ```

4. **Test Ollama Directly:**
   ```bash
   curl http://localhost:11434/api/tags
   ```
   Should return JSON with available models

5. **Check Ollama Logs:**
   ```bash
   docker logs $(docker ps -qf "name=ollama")
   ```

---

### 5. Database Issues

#### Error: Cannot connect to database

**For H2 (default):**
1. Check data directory exists:
   ```bash
   mkdir -p ./data
   ```

2. Check file permissions:
   ```bash
   ls -la ./data/
   ```

3. Access H2 Console:
   - URL: http://localhost:8080/h2-console
   - JDBC URL: `jdbc:h2:file:./data/emailchat`
   - Username: `sa`
   - Password: `password`

**For PostgreSQL:**
1. Check container is running:
   ```bash
   docker ps | grep postgres
   ```

2. Restart if needed:
   ```bash
   docker-compose restart postgres
   ```

---

## Debugging Workflow

### Step 1: Test Connection
1. Go to: http://localhost:8080/monitoring
2. Click "Test Connection" for your account
3. Note which test fails (IMAP, SMTP, or both)

### Step 2: Check Logs
```bash
# Watch logs in real-time
tail -f logs/ai-email-chat.log

# Search for errors
grep "✗" logs/ai-email-chat.log

# Search for successful processing
grep "✓" logs/ai-email-chat.log
```

### Step 3: Verify Settings
Common mistakes:
- ❌ Using regular password instead of app password
- ❌ Wrong IMAP/SMTP host
- ❌ IMAP not enabled in email provider settings
- ❌ 2FA enabled but no app password generated
- ❌ Subject doesn't start with `[AI_REQUEST]`

### Step 4: Check Service Status
Visit: http://localhost:8080/actuator/health

Should show:
```json
{
  "status": "UP"
}
```

---

## Log Messages Reference

### Success Indicators (✓)
- `✓ Successfully connected to IMAP` - IMAP working
- `✓ INBOX opened successfully` - Can access mailbox
- `✓ Message matches filter!` - Found [AI_REQUEST] email
- `✓ AI response generated` - Ollama responded
- `✓ Email sent successfully` - Reply sent

### Error Indicators (✗)
- `✗ Authentication failed` - Bad credentials or 2FA issue
- `✗ Could not connect to SMTP host` - Network/config issue
- `✗ Failed to generate AI response` - Ollama problem

### Info Indicators
- `=== Starting email check cycle ===` - Polling started (every 60s)
- `Found X unread messages` - Emails detected
- `⊗ Skipping message` - Doesn't match filter

---

## Still Having Issues?

1. **Enable Debug Mode:**
   Edit `application.yml`:
   ```yaml
   logging:
     level:
       jakarta.mail: DEBUG  # Uncomment this line
   ```

2. **Check Application Health:**
   ```bash
   curl http://localhost:8080/actuator/health
   ```

3. **Verify Java Version:**
   ```bash
   java -version
   # Should be Java 21 or higher
   ```

4. **Rebuild Application:**
   ```bash
   ./gradlew clean build
   ./gradlew bootRun
   ```

5. **Check GitHub Issues:**
   Report your issue with:
   - Log output (from `logs/ai-email-chat.log`)
   - Connection test results
   - Email provider (Gmail/Outlook/Yahoo/other)
   - Error messages

---

## Quick Reference: Email Provider Settings

| Provider | IMAP Host | IMAP Port | SMTP Host | SMTP Port | SSL | Notes |
|----------|-----------|-----------|-----------|-----------|-----|-------|
| **Gmail** | imap.gmail.com | 993 | smtp.gmail.com | 587 | ✓ | Needs app password |
| **Outlook/Hotmail** | outlook.office365.com | 993 | smtp.office365.com | 587 | ✓ | Enable IMAP first! |
| **Yahoo** | imap.mail.yahoo.com | 993 | smtp.mail.yahoo.com | 587 | ✓ | Needs app password |
| **iCloud** | imap.mail.me.com | 993 | smtp.mail.me.com | 587 | ✓ | Needs app password |
| **ProtonMail** | 127.0.0.1 | 1143 | 127.0.0.1 | 1025 | ✗ | Needs Bridge app |

---

## Performance Tips

1. **Adjust Poll Rate:**
   - Default: 60 seconds (60000ms)
   - Edit `application.yml`:
     ```yaml
     ai:
       email:
         chat:
           poll-rate: 30000  # 30 seconds
     ```

2. **Use Faster Ollama Model:**
   - Current: `llama3.2`
   - Try: `phi`, `tinyllama` for faster responses
   - Edit `application.yml`:
     ```yaml
     ollama:
       model: phi
     ```

3. **Monitor Resource Usage:**
   ```bash
   docker stats
   ```
