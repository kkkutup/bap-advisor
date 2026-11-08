package tr.edu.mersin.bapadvisor.submission;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, UUID> {
    Optional<SubmissionVersion> findBySubmissionIdAndVersionNo(UUID submissionId, int versionNo);
    List<SubmissionVersion> findBySubmissionIdOrderByVersionNoDesc(UUID submissionId);
    Optional<SubmissionVersion> findTopBySubmissionIdOrderByVersionNoDesc(UUID submissionId);
}
