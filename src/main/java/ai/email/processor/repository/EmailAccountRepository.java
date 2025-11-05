package ai.email.processor.repository;

import ai.email.processor.entity.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmailAccountRepository extends JpaRepository<EmailAccount, Long> {

    Optional<EmailAccount> findByEmailAddress(String emailAddress);

    List<EmailAccount> findByActiveTrue();

    boolean existsByEmailAddress(String emailAddress);
}
