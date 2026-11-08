package tr.edu.mersin.bapadvisor.limit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectLimitConfigRepository extends JpaRepository<ProjectLimitConfig, UUID> {
    List<ProjectLimitConfig> findByFiscalYearAndIsActiveTrue(Integer fiscalYear);
    List<ProjectLimitConfig> findByFiscalYear(Integer fiscalYear);
    Optional<ProjectLimitConfig> findByFiscalYearAndCode(Integer fiscalYear, String code);
}
