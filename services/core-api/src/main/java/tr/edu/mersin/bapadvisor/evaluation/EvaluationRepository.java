package tr.edu.mersin.bapadvisor.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {
    Optional<Evaluation> findTopByVersionIdOrderByCompletedAtDesc(UUID versionId);
}
