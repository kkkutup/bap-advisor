package tr.edu.mersin.bapadvisor.submission;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {
    Optional<Submission> findByReferenceNo(String referenceNo);

    @Query("SELECT COUNT(s) FROM Submission s WHERE s.referenceNo LIKE :prefixPattern")
    long countByReferenceNoStartingWith(@Param("prefixPattern") String prefixPattern);

    @Query("SELECT s FROM Submission s WHERE " +
            "(:status IS NULL OR s.status = :status) AND " +
            "(:submissionType IS NULL OR s.submissionType = :submissionType) AND " +
            "(:projectCode = '' OR s.projectCode = :projectCode) AND " +
            "(:assignedOfficerId IS NULL OR s.assignedOfficerId = :assignedOfficerId) AND " +
            "(:searchPattern = '%' OR LOWER(s.referenceNo) LIKE :searchPattern OR " +
            "LOWER(s.title) LIKE :searchPattern OR " +
            "LOWER(s.applicantName) LIKE :searchPattern)")
    Page<Submission> findWithFilters(
            @Param("status") SubmissionStatus status,
            @Param("submissionType") SubmissionType submissionType,
            @Param("projectCode") String projectCode,
            @Param("assignedOfficerId") UUID assignedOfficerId,
            @Param("searchPattern") String searchPattern,
            Pageable pageable
    );
}
