package ai.email.processor.service;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
import ai.email.processor.oauth2.OAuth2Authenticator;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.search.FlagTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Properties;

@Service
public class EmailReceiverService {

    private static final Logger logger = LoggerFactory.getLogger(EmailReceiverService.class);

    private final EmailAccountService emailAccountService;
    private final ConversationService conversationService;
    private final EmailSenderService emailSenderService;
    private final OAuth2Authenticator oauth2Authenticator;

    @Value("${ai.email.chat.subject-filter:[AI_REQUEST]}")
    private String subjectFilter;

    public EmailReceiverService(EmailAccountService emailAccountService,
                               ConversationService conversationService,
                               EmailSenderService emailSenderService,
                               OAuth2Authenticator oauth2Authenticator) {
        this.emailAccountService = emailAccountService;
        this.conversationService = conversationService;
        this.emailSenderService = emailSenderService;
        this.oauth2Authenticator = oauth2Authenticator;
    }

    @Scheduled(fixedDelayString = "${ai.email.chat.poll-rate:60000}")
    public void checkEmails() {
        logger.info("=== Starting email check cycle ===");
        List<EmailAccount> activeAccounts = emailAccountService.getActiveAccounts();
        logger.info("Found {} active email accounts to check", activeAccounts.size());

        if (activeAccounts.isEmpty()) {
            logger.warn("No active email accounts configured! Add accounts through the web UI.");
        }

        for (EmailAccount account : activeAccounts) {
            try {
                logger.info("Checking emails for account: {}", account.getEmailAddress());
                processAccountEmails(account);
            } catch (Exception e) {
                logger.error("Error processing emails for account: {}", account.getEmailAddress(), e);
                logger.error("Error details - Host: {}, Port: {}, SSL: {}",
                    account.getImapHost(), account.getImapPort(), account.isUseSSL());
            }
        }
        logger.info("=== Email check cycle completed ===");
    }

    private void processAccountEmails(EmailAccount account) throws MessagingException, IOException {
        logger.debug("Setting up IMAP connection for {}", account.getEmailAddress());
        logger.debug("IMAP Settings - Host: {}, Port: {}, SSL: {}, Username: {}, AuthType: {}",
            account.getImapHost(), account.getImapPort(), account.isUseSSL(), account.getUsername(), account.getAuthType());

        Store store = null;

        try {
            logger.debug("Connecting to IMAP server...");

            // Use OAuth2 or basic authentication based on account type
            if (account.isOAuth2()) {
                logger.info("Using OAuth2 authentication for {}", account.getEmailAddress());
                store = oauth2Authenticator.connectImap(account);
            } else {
                logger.info("Using basic authentication for {}", account.getEmailAddress());
                Properties props = new Properties();
                props.put("mail.store.protocol", account.isUseSSL() ? "imaps" : "imap");
                props.put("mail.imap.host", account.getImapHost());
                props.put("mail.imap.port", account.getImapPort());
                props.put("mail.debug", "false");

                if (account.isUseSSL()) {
                    props.put("mail.imap.ssl.enable", "true");
                    props.put("mail.imap.ssl.trust", "*");
                } else {
                    props.put("mail.imap.starttls.enable", "true");
                }

                Session session = Session.getInstance(props);
                store = session.getStore();
                store.connect(account.getImapHost(), account.getUsername(), account.getPassword());
            }

            logger.info("✓ Successfully connected to IMAP server for {}", account.getEmailAddress());

            logger.debug("Opening INBOX folder...");
            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);
            logger.info("✓ INBOX opened successfully. Total messages: {}, Unread: {}",
                inbox.getMessageCount(), inbox.getUnreadMessageCount());

            // Search for unread messages
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            logger.info("Found {} unread messages for account: {}", messages.length, account.getEmailAddress());

            if (messages.length == 0) {
                logger.debug("No unread messages to process for {}", account.getEmailAddress());
            }

            for (Message message : messages) {
                try {
                    logger.debug("Processing message #{}", message.getMessageNumber());
                    processMessage(account, message);
                    // Mark as read after processing
                    message.setFlag(Flags.Flag.SEEN, true);
                    logger.debug("✓ Message #{} processed and marked as read", message.getMessageNumber());
                } catch (Exception e) {
                    logger.error("✗ Error processing message #{}: {}", message.getMessageNumber(), e.getMessage(), e);
                }
            }

            inbox.close(false);
            logger.debug("INBOX closed");
        } catch (AuthenticationFailedException e) {
            logger.error("✗ Authentication failed for {}", account.getEmailAddress());
            logger.error("This could be due to:");
            logger.error("  - Incorrect password");
            logger.error("  - 2FA enabled without app-specific password");
            logger.error("  - IMAP access not enabled");
            logger.error("  - Account security settings blocking access");
            throw e;
        } catch (MessagingException e) {
            logger.error("✗ Messaging error for {}: {}", account.getEmailAddress(), e.getMessage(), e);
            throw e;
        } finally {
            if (store != null && store.isConnected()) {
                store.close();
                logger.debug("Store connection closed");
            }
        }
    }

    private void processMessage(EmailAccount account, Message message) throws MessagingException, IOException {
        String subject = message.getSubject();
        logger.debug("Message subject: '{}'", subject);
        logger.debug("Required filter: '{}'", subjectFilter);

        // Check if subject starts with the filter
        if (subject == null || !subject.startsWith(subjectFilter)) {
            logger.debug("⊗ Skipping message - subject does not start with filter '{}': {}", subjectFilter, subject);
            return;
        }

        logger.info("✓ Message matches filter! Processing AI request...");

        String from = message.getFrom()[0].toString();
        // Extract email address from "Name <email@example.com>" format
        String senderEmail = extractEmail(from);
        logger.debug("From: {} -> Extracted email: {}", from, senderEmail);

        String content = getTextFromMessage(message);
        logger.debug("Message content length: {} characters", content.length());
        logger.debug("Content preview: {}", content.length() > 100 ? content.substring(0, 100) + "..." : content);

        String messageId = message.getHeader("Message-ID") != null ? message.getHeader("Message-ID")[0] : null;
        logger.debug("Message-ID: {}", messageId);

        logger.info("➤ Processing AI request from {} with subject: {}", senderEmail, subject);

        try {
            // Get or create conversation
            logger.debug("Getting or creating conversation with {}", senderEmail);
            Conversation conversation = conversationService.getOrCreateConversation(account, senderEmail);
            logger.debug("✓ Conversation ID: {}", conversation.getId());

            // Add user message to conversation
            logger.debug("Adding user message to conversation");
            conversationService.addUserMessage(conversation, content, subject, messageId);
            logger.debug("✓ User message saved");

            // Generate AI response
            logger.info("Generating AI response using Ollama...");
            String aiResponse = conversationService.generateAIResponse(conversation, content);
            logger.info("✓ AI response generated ({} characters)", aiResponse.length());
            logger.debug("AI response preview: {}", aiResponse.length() > 100 ? aiResponse.substring(0, 100) + "..." : aiResponse);

            // Send reply
            logger.info("Sending reply email to {}", senderEmail);
            emailSenderService.sendReply(account, senderEmail, subject, aiResponse);
            logger.info("✓ AI response sent successfully to {}", senderEmail);
        } catch (Exception e) {
            logger.error("✗ Failed to process message and send reply", e);
            throw e;
        }
    }

    private String extractEmail(String fromAddress) {
        // Extract email from "Name <email@example.com>" format
        if (fromAddress.contains("<") && fromAddress.contains(">")) {
            int start = fromAddress.indexOf('<') + 1;
            int end = fromAddress.indexOf('>');
            return fromAddress.substring(start, end);
        }
        return fromAddress;
    }

    private String getTextFromMessage(Message message) throws MessagingException, IOException {
        String result = "";
        if (message.isMimeType("text/plain")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("text/html")) {
            result = message.getContent().toString();
        } else if (message.isMimeType("multipart/*")) {
            MimeMultipart mimeMultipart = (MimeMultipart) message.getContent();
            result = getTextFromMimeMultipart(mimeMultipart);
        }
        return result;
    }

    private String getTextFromMimeMultipart(MimeMultipart mimeMultipart) throws MessagingException, IOException {
        StringBuilder result = new StringBuilder();
        int count = mimeMultipart.getCount();
        for (int i = 0; i < count; i++) {
            BodyPart bodyPart = mimeMultipart.getBodyPart(i);
            if (bodyPart.isMimeType("text/plain")) {
                result.append(bodyPart.getContent());
                break; // Prefer plain text
            } else if (bodyPart.isMimeType("text/html")) {
                String html = (String) bodyPart.getContent();
                result.append(html);
            } else if (bodyPart.getContent() instanceof MimeMultipart) {
                result.append(getTextFromMimeMultipart((MimeMultipart) bodyPart.getContent()));
            }
        }
        return result.toString();
    }
}
