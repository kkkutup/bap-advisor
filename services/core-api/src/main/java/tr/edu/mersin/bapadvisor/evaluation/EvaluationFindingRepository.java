package tr.edu.mersin.bapadvisor.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EvaluationFindingRepository extends JpaRepository<EvaluationFinding, UUID> {
    List<EvaluationFinding> findByEvaluationId(UUID evaluationId);
}
