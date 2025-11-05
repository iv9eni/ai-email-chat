package ai.email.processor.service;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
import ai.email.processor.entity.Message;
import ai.email.processor.repository.ConversationRepository;
import ai.email.processor.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ConversationService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatClient chatClient;

    public ConversationService(ConversationRepository conversationRepository,
                              MessageRepository messageRepository,
                              OllamaChatModel ollamaChatModel) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatClient = ChatClient.builder(ollamaChatModel).build();
    }

    public Conversation getOrCreateConversation(EmailAccount emailAccount, String participantEmail) {
        Optional<Conversation> existing = conversationRepository
            .findByEmailAccountAndParticipantEmail(emailAccount, participantEmail);

        if (existing.isPresent()) {
            logger.debug("Found existing conversation (ID: {}) with {}", existing.get().getId(), participantEmail);
            return existing.get();
        } else {
            logger.info("Creating new conversation with {}", participantEmail);
            Conversation conversation = new Conversation(emailAccount, participantEmail);
            Conversation saved = conversationRepository.save(conversation);
            logger.debug("✓ New conversation created with ID: {}", saved.getId());
            return saved;
        }
    }

    public Message addUserMessage(Conversation conversation, String content, String subject, String messageId) {
        // Check if message already exists (prevent duplicates)
        if (messageId != null && messageRepository.existsByEmailMessageId(messageId)) {
            logger.warn("Message with ID {} already exists, skipping duplicate", messageId);
            return messageRepository.findByEmailMessageId(messageId).orElseThrow();
        }

        logger.debug("Adding user message to conversation {}", conversation.getId());
        Message message = new Message(content, Message.MessageRole.USER, subject);
        message.setEmailMessageId(messageId);
        conversation.addMessage(message);

        messageRepository.save(message);
        conversationRepository.save(conversation);
        logger.debug("✓ User message saved (ID: {})", message.getId());

        return message;
    }

    public String generateAIResponse(Conversation conversation, String userMessage) {
        logger.info("Generating AI response for conversation {}", conversation.getId());

        // Build context from conversation history
        List<Message> history = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);
        logger.debug("Conversation history: {} messages", history.size());

        // Build the prompt with conversation context
        var promptBuilder = new StringBuilder();
        promptBuilder.append("You are a helpful AI assistant responding to emails. ");
        promptBuilder.append("Previous conversation:\n\n");

        for (Message msg : history) {
            if (msg.getRole() == Message.MessageRole.USER) {
                promptBuilder.append("User: ").append(msg.getContent()).append("\n\n");
            } else {
                promptBuilder.append("Assistant: ").append(msg.getContent()).append("\n\n");
            }
        }

        promptBuilder.append("Please respond to the latest message in a helpful and professional manner.");

        logger.debug("Prompt length: {} characters", promptBuilder.length());
        logger.debug("Calling Ollama API...");

        try {
            // Generate response using Ollama
            String response = chatClient.prompt()
                .system(promptBuilder.toString())
                .user(userMessage)
                .call()
                .content();

            logger.info("✓ Received response from Ollama ({} characters)", response.length());

            // Save AI response to database
            Message aiMessage = new Message(response, Message.MessageRole.ASSISTANT);
            conversation.addMessage(aiMessage);
            messageRepository.save(aiMessage);
            conversationRepository.save(conversation);
            logger.debug("✓ AI response saved to database (Message ID: {})", aiMessage.getId());

            return response;
        } catch (Exception e) {
            logger.error("✗ Failed to generate AI response from Ollama", e);
            logger.error("Check that:");
            logger.error("  - Ollama is running (docker ps | grep ollama)");
            logger.error("  - The model is pulled (docker exec <ollama-container> ollama list)");
            logger.error("  - Ollama is accessible at the configured base-url");
            throw new RuntimeException("Failed to generate AI response", e);
        }
    }

    public List<Conversation> getConversationsByAccount(EmailAccount emailAccount) {
        return conversationRepository.findByEmailAccountOrderByLastMessageAtDesc(emailAccount);
    }

    public Optional<Conversation> getConversationById(Long id) {
        return conversationRepository.findById(id);
    }

    public List<Message> getConversationMessages(Conversation conversation) {
        return messageRepository.findByConversationOrderByCreatedAtAsc(conversation);
    }
}
