# OAuth2 Setup Guide

This guide explains how to configure OAuth2 authentication for email providers.

## Overview

The application now supports OAuth2 authentication for:
- ✅ **Microsoft (Outlook/Hotmail)** - Fully implemented
- 🚧 **Google (Gmail)** - Infrastructure ready, provider implementation pending
- 🚧 **Yahoo Mail** - Infrastructure ready, provider implementation pending

## Why OAuth2?

**Microsoft Requirement**: As of September 16, 2024, Microsoft permanently disabled basic authentication (username/password and app passwords) for personal Outlook.com/Hotmail accounts. OAuth2 is now **required** for these accounts.

**Benefits**:
- ✅ No password storage - more secure
- ✅ Granular permissions - only email access
- ✅ Revocable - users can revoke access anytime
- ✅ Industry standard - future-proof

## Microsoft (Outlook/Hotmail) Setup

### Step 1: Register Your Application in Azure

1. **Go to Azure Portal**
   - Visit: https://portal.azure.com/
   - Sign in with your Microsoft account

2. **Navigate to App Registrations**
   - Search for "App registrations" in the top search bar
   - Click on "App registrations"

3. **Create New Registration**
   - Click "+ New registration"
   - Fill in the details:
     - **Name**: `AI Email Chat` (or your preferred name)
     - **Supported account types**: Select "Personal Microsoft accounts only"
     - **Redirect URI**:
       - Platform: `Web`
       - URL: `http://localhost:8080/oauth2/callback/microsoft`
       - (Change `localhost:8080` to your domain in production)
   - Click "Register"

4. **Note Your Application (Client) ID**
   - On the Overview page, copy the **Application (client) ID**
   - You'll need this for configuration

### Step 2: Create Client Secret

1. **Go to Certificates & secrets**
   - In your app registration, click "Certificates & secrets" in the left menu
   - Click "+ New client secret"

2. **Create Secret**
   - Description: `AI Email Chat Secret`
   - Expires: Choose duration (recommend 24 months)
   - Click "Add"

3. **Copy the Secret Value**
   - **IMPORTANT**: Copy the secret value NOW - it won't be shown again!
   - Store it securely

### Step 3: Configure API Permissions

1. **Go to API permissions**
   - Click "API permissions" in the left menu
   - Click "+ Add a permission"

2. **Add Microsoft Graph Permissions**
   - Select "Microsoft Graph"
   - Select "Delegated permissions"
   - Add these permissions:
     - `offline_access` (to get refresh tokens)
     - `User.Read` (to get user email)

3. **Add Outlook Permissions**
   - Click "+ Add a permission" again
   - Select "Microsoft Graph"
   - Select "Delegated permissions"
   - Search and add:
     - `IMAP.AccessAsUser.All`
     - `SMTP.Send`

   **Note**: If you don't see IMAP/SMTP permissions under Microsoft Graph:
   - These are part of Office 365 Exchange permissions
   - They should appear when you search for "IMAP" and "SMTP"

4. **Grant Admin Consent** (Optional for personal accounts)
   - If you're using a work/school account, click "Grant admin consent"
   - For personal accounts, this is not required

### Step 4: Configure Environment Variables

Set these environment variables before starting the application:

```bash
export MICROSOFT_CLIENT_ID="your-client-id-here"
export MICROSOFT_CLIENT_SECRET="your-client-secret-here"
```

Or add them to your application.yml:

```yaml
app:
  oauth2:
    redirect-base-url: http://localhost:8080
    microsoft:
      client-id: your-client-id-here
      client-secret: your-client-secret-here
```

### Step 5: Test the Connection

1. **Start the Application**
   ```bash
   ./gradlew bootRun
   ```

   Or with Docker:
   ```bash
   docker compose up
   ```

2. **Open the Web UI**
   - Navigate to: http://localhost:8080/accounts
   - Click "Connect Microsoft (Outlook/Hotmail)"

3. **Authorize Access**
   - You'll be redirected to Microsoft login
   - Sign in with your Outlook/Hotmail account
   - Review and accept the permissions
   - You'll be redirected back to the application

4. **Verify Connection**
   - Your account should now appear in the accounts list
   - Look for "🔐 OAuth2 (microsoft)" in the auth type
   - Token status should show "✓ Valid"

## Troubleshooting

### Common Issues

**Issue**: "AADSTS700016: Application not found"
- **Solution**: Double-check your Client ID is correct
- Make sure you copied the entire ID from Azure Portal

**Issue**: "AADSTS7000215: Invalid client secret"
- **Solution**: Your client secret may be incorrect or expired
- Generate a new secret in Azure Portal

**Issue**: "Redirect URI mismatch"
- **Solution**: Ensure the redirect URI in Azure matches exactly:
  - Azure: `http://localhost:8080/oauth2/callback/microsoft`
  - Application: Must match the `app.oauth2.redirect-base-url` setting

**Issue**: "Insufficient permissions"
- **Solution**: Make sure all required permissions are added:
  - `offline_access`
  - `User.Read`
  - `IMAP.AccessAsUser.All`
  - `SMTP.Send`

**Issue**: "Token expired" after some time
- **Solution**: This is normal! The application automatically refreshes tokens.
- If refresh fails, click the "🔄 Reconnect" button on the account card

### Testing Email Connection

After OAuth2 setup, test the connection:

```bash
curl http://localhost:8080/api/diagnostics/test-connection/1
```

You should see successful IMAP and SMTP connections.

## Security Best Practices

1. **Never commit secrets to Git**
   - Use environment variables for Client ID and Secret
   - Add sensitive files to `.gitignore`

2. **Use HTTPS in Production**
   - Update redirect URI to use `https://`
   - Update `app.oauth2.redirect-base-url` to production domain

3. **Rotate Secrets Regularly**
   - Create new client secrets before old ones expire
   - Azure allows multiple active secrets for smooth rotation

4. **Monitor Token Usage**
   - Check application logs for failed token refreshes
   - Users should reconnect if they see "Token expired"

## Production Deployment

For production, update these settings:

1. **Update Redirect URI in Azure**
   - Go to your app registration
   - Add production redirect URI: `https://yourdomain.com/oauth2/callback/microsoft`

2. **Update Application Configuration**
   ```yaml
   app:
     oauth2:
       redirect-base-url: https://yourdomain.com
   ```

3. **Set Environment Variables**
   - Use your hosting platform's secrets manager
   - Never hard-code credentials in application.yml

## Future Providers

### Google (Gmail)

To add Google OAuth2 support:
1. Create project in Google Cloud Console
2. Enable Gmail API
3. Create OAuth2 credentials
4. Implement `GoogleOAuth2Provider` (similar to Microsoft)

### Yahoo Mail

To add Yahoo OAuth2 support:
1. Register app at Yahoo Developer Network
2. Get Client ID and Secret
3. Implement `YahooOAuth2Provider` (similar to Microsoft)

## Architecture

The OAuth2 implementation consists of:

- **OAuth2Provider** interface - Common contract for all providers
- **OAuth2TokenService** - Token management and refresh logic
- **OAuth2Controller** - Handles OAuth callbacks and redirects
- **OAuth2Authenticator** - JavaMail XOAUTH2 authentication
- **MicrosoftOAuth2Provider** - Microsoft-specific implementation

Providers are registered at startup via `OAuth2Configuration` class.

## References

- [Microsoft OAuth 2.0 Documentation](https://docs.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-auth-code-flow)
- [Microsoft Graph API](https://docs.microsoft.com/en-us/graph/overview)
- [JavaMail OAuth2 Support](https://javaee.github.io/javamail/)

## Support

If you encounter issues:
1. Check the application logs: `logs/ai-email-chat.log`
2. Verify all Azure permissions are correct
3. Test with the diagnostic endpoint
4. Check that tokens haven't expired

For questions, open an issue on GitHub.
