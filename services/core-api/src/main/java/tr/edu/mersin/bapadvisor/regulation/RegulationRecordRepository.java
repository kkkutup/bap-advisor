package tr.edu.mersin.bapadvisor.regulation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RegulationRecordRepository extends JpaRepository<RegulationRecord, UUID> {
    Optional<RegulationRecord> findBySlug(String slug);
    List<RegulationRecord> findByIsActiveTrue();
}
