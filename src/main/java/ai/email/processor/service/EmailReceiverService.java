package ai.email.processor.service;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
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

    @Value("${ai.email.chat.subject-filter:[AI_REQUEST]}")
    private String subjectFilter;

    public EmailReceiverService(EmailAccountService emailAccountService,
                               ConversationService conversationService,
                               EmailSenderService emailSenderService) {
        this.emailAccountService = emailAccountService;
        this.conversationService = conversationService;
        this.emailSenderService = emailSenderService;
    }

    @Scheduled(fixedDelayString = "${ai.email.chat.poll-rate:60000}")
    public void checkEmails() {
        List<EmailAccount> activeAccounts = emailAccountService.getActiveAccounts();

        for (EmailAccount account : activeAccounts) {
            try {
                processAccountEmails(account);
            } catch (Exception e) {
                logger.error("Error processing emails for account: {}", account.getEmailAddress(), e);
            }
        }
    }

    private void processAccountEmails(EmailAccount account) throws MessagingException, IOException {
        Properties props = new Properties();
        props.put("mail.store.protocol", account.isUseSSL() ? "imaps" : "imap");
        props.put("mail.imap.host", account.getImapHost());
        props.put("mail.imap.port", account.getImapPort());

        if (account.isUseSSL()) {
            props.put("mail.imap.ssl.enable", "true");
        } else {
            props.put("mail.imap.starttls.enable", "true");
        }

        Session session = Session.getInstance(props);
        Store store = null;

        try {
            store = session.getStore();
            store.connect(account.getImapHost(), account.getUsername(), account.getPassword());

            Folder inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Search for unread messages
            Message[] messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            logger.info("Found {} unread messages for account: {}", messages.length, account.getEmailAddress());

            for (Message message : messages) {
                try {
                    processMessage(account, message);
                    // Mark as read after processing
                    message.setFlag(Flags.Flag.SEEN, true);
                } catch (Exception e) {
                    logger.error("Error processing message: {}", message.getMessageNumber(), e);
                }
            }

            inbox.close(false);
        } finally {
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    private void processMessage(EmailAccount account, Message message) throws MessagingException, IOException {
        String subject = message.getSubject();

        // Check if subject starts with the filter
        if (subject == null || !subject.startsWith(subjectFilter)) {
            logger.debug("Skipping message without filter prefix: {}", subject);
            return;
        }

        String from = message.getFrom()[0].toString();
        // Extract email address from "Name <email@example.com>" format
        String senderEmail = extractEmail(from);

        String content = getTextFromMessage(message);
        String messageId = message.getHeader("Message-ID") != null ? message.getHeader("Message-ID")[0] : null;

        logger.info("Processing AI request from {} with subject: {}", senderEmail, subject);

        // Get or create conversation
        Conversation conversation = conversationService.getOrCreateConversation(account, senderEmail);

        // Add user message to conversation
        conversationService.addUserMessage(conversation, content, subject, messageId);

        // Generate AI response
        String aiResponse = conversationService.generateAIResponse(conversation, content);

        // Send reply
        emailSenderService.sendReply(account, senderEmail, subject, aiResponse);

        logger.info("Sent AI response to {}", senderEmail);
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
