package ai.email.processor.repository;

import ai.email.processor.entity.Conversation;
import ai.email.processor.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    Optional<Message> findByEmailMessageId(String emailMessageId);

    boolean existsByEmailMessageId(String emailMessageId);
}
