package ai.email.processor.service;

import ai.email.processor.entity.EmailAccount;
import ai.email.processor.repository.EmailAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class EmailAccountService {

    private final EmailAccountRepository emailAccountRepository;

    public EmailAccountService(EmailAccountRepository emailAccountRepository) {
        this.emailAccountRepository = emailAccountRepository;
    }

    public EmailAccount createAccount(EmailAccount account) {
        if (emailAccountRepository.existsByEmailAddress(account.getEmailAddress())) {
            throw new IllegalArgumentException("Email account already exists: " + account.getEmailAddress());
        }
        return emailAccountRepository.save(account);
    }

    public EmailAccount updateAccount(Long id, EmailAccount updatedAccount) {
        EmailAccount account = emailAccountRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Email account not found: " + id));

        account.setDisplayName(updatedAccount.getDisplayName());
        account.setImapHost(updatedAccount.getImapHost());
        account.setImapPort(updatedAccount.getImapPort());
        account.setSmtpHost(updatedAccount.getSmtpHost());
        account.setSmtpPort(updatedAccount.getSmtpPort());
        account.setUsername(updatedAccount.getUsername());

        if (updatedAccount.getPassword() != null && !updatedAccount.getPassword().isEmpty()) {
            account.setPassword(updatedAccount.getPassword());
        }

        account.setUseSSL(updatedAccount.isUseSSL());
        account.setActive(updatedAccount.isActive());

        return emailAccountRepository.save(account);
    }

    public void deleteAccount(Long id) {
        if (!emailAccountRepository.existsById(id)) {
            throw new IllegalArgumentException("Email account not found: " + id);
        }
        emailAccountRepository.deleteById(id);
    }

    public Optional<EmailAccount> getAccount(Long id) {
        return emailAccountRepository.findById(id);
    }

    public Optional<EmailAccount> getAccountByEmail(String emailAddress) {
        return emailAccountRepository.findByEmailAddress(emailAddress);
    }

    public List<EmailAccount> getAllAccounts() {
        return emailAccountRepository.findAll();
    }

    public List<EmailAccount> getActiveAccounts() {
        return emailAccountRepository.findByActiveTrue();
    }

    public void toggleAccountStatus(Long id, boolean active) {
        EmailAccount account = emailAccountRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Email account not found: " + id));
        account.setActive(active);
        emailAccountRepository.save(account);
    }
}
