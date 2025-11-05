package ai.email.processor.controller;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
import ai.email.processor.entity.Message;
import ai.email.processor.service.ConversationService;
import ai.email.processor.service.EmailAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final EmailAccountService emailAccountService;

    public ConversationController(ConversationService conversationService,
                                 EmailAccountService emailAccountService) {
        this.conversationService = conversationService;
        this.emailAccountService = emailAccountService;
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<List<Conversation>> getConversationsByAccount(@PathVariable Long accountId) {
        return emailAccountService.getAccount(accountId)
            .map(account -> ResponseEntity.ok(conversationService.getConversationsByAccount(account)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conversation> getConversation(@PathVariable Long id) {
        return conversationService.getConversationById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<Message>> getConversationMessages(@PathVariable Long id) {
        return conversationService.getConversationById(id)
            .map(conversation -> ResponseEntity.ok(conversationService.getConversationMessages(conversation)))
            .orElse(ResponseEntity.notFound().build());
    }
}
