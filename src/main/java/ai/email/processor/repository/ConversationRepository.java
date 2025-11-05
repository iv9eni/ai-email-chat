package ai.email.processor.repository;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.EmailAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByEmailAccountAndParticipantEmail(
        EmailAccount emailAccount,
        String participantEmail
    );

    List<Conversation> findByEmailAccount(EmailAccount emailAccount);

    List<Conversation> findByEmailAccountOrderByLastMessageAtDesc(EmailAccount emailAccount);
}
