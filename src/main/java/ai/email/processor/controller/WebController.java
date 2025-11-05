package ai.email.processor.controller;

import ai.email.processor.service.EmailAccountService;
import ai.email.processor.service.ConversationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class WebController {

    private final EmailAccountService emailAccountService;
    private final ConversationService conversationService;

    public WebController(EmailAccountService emailAccountService,
                        ConversationService conversationService) {
        this.emailAccountService = emailAccountService;
        this.conversationService = conversationService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("accounts", emailAccountService.getAllAccounts());
        return "index";
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("accounts", emailAccountService.getAllAccounts());
        return "accounts";
    }

    @GetMapping("/conversations/{accountId}")
    public String conversations(@PathVariable Long accountId, Model model) {
        return emailAccountService.getAccount(accountId)
            .map(account -> {
                model.addAttribute("account", account);
                model.addAttribute("conversations", conversationService.getConversationsByAccount(account));
                return "conversations";
            })
            .orElse("redirect:/accounts");
    }

    @GetMapping("/conversation/{id}")
    public String conversation(@PathVariable Long id, Model model) {
        return conversationService.getConversationById(id)
            .map(conversation -> {
                model.addAttribute("conversation", conversation);
                model.addAttribute("messages", conversationService.getConversationMessages(conversation));
                return "conversation";
            })
            .orElse("redirect:/accounts");
    }

    @GetMapping("/monitoring")
    public String monitoring() {
        return "monitoring";
    }
}
