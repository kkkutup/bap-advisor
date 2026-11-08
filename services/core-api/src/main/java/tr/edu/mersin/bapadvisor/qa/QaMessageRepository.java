package tr.edu.mersin.bapadvisor.qa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QaMessageRepository extends JpaRepository<QaMessage, UUID> {
    List<QaMessage> findByVersionIdOrderByCreatedAtAsc(UUID versionId);
}
