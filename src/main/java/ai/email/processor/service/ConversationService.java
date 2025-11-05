package ai.email.processor.service;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
import ai.email.processor.entity.Message;
import ai.email.processor.repository.ConversationRepository;
import ai.email.processor.repository.MessageRepository;
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
        return conversationRepository
            .findByEmailAccountAndParticipantEmail(emailAccount, participantEmail)
            .orElseGet(() -> {
                Conversation conversation = new Conversation(emailAccount, participantEmail);
                return conversationRepository.save(conversation);
            });
    }

    public Message addUserMessage(Conversation conversation, String content, String subject, String messageId) {
        // Check if message already exists (prevent duplicates)
        if (messageId != null && messageRepository.existsByEmailMessageId(messageId)) {
            return messageRepository.findByEmailMessageId(messageId).orElseThrow();
        }

        Message message = new Message(content, Message.MessageRole.USER, subject);
        message.setEmailMessageId(messageId);
        conversation.addMessage(message);

        messageRepository.save(message);
        conversationRepository.save(conversation);

        return message;
    }

    public String generateAIResponse(Conversation conversation, String userMessage) {
        // Build context from conversation history
        List<Message> history = messageRepository.findByConversationOrderByCreatedAtAsc(conversation);

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

        // Generate response using Ollama
        String response = chatClient.prompt()
            .system(promptBuilder.toString())
            .user(userMessage)
            .call()
            .content();

        // Save AI response to database
        Message aiMessage = new Message(response, Message.MessageRole.ASSISTANT);
        conversation.addMessage(aiMessage);
        messageRepository.save(aiMessage);
        conversationRepository.save(conversation);

        return response;
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
