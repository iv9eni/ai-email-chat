package ai.email.processor.controller;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.service.EmailAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class EmailAccountController {

    private final EmailAccountService emailAccountService;

    public EmailAccountController(EmailAccountService emailAccountService) {
        this.emailAccountService = emailAccountService;
    }

    @GetMapping
    public ResponseEntity<List<EmailAccount>> getAllAccounts() {
        return ResponseEntity.ok(emailAccountService.getAllAccounts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmailAccount> getAccount(@PathVariable Long id) {
        return emailAccountService.getAccount(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createAccount(@RequestBody EmailAccount account) {
        try {
            EmailAccount created = emailAccountService.createAccount(account);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAccount(@PathVariable Long id, @RequestBody EmailAccount account) {
        try {
            EmailAccount updated = emailAccountService.updateAccount(id, account);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAccount(@PathVariable Long id) {
        try {
            emailAccountService.deleteAccount(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<?> toggleAccountStatus(@PathVariable Long id, @RequestParam boolean active) {
        try {
            emailAccountService.toggleAccountStatus(id, active);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
