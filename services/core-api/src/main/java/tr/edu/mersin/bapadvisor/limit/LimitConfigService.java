package tr.edu.mersin.bapadvisor.limit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;
import tr.edu.mersin.bapadvisor.exception.BadRequestException;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LimitConfigService {

    private final ProjectLimitConfigRepository limitConfigRepository;
    private final ObjectMapper objectMapper;

    public LimitConfigService(ProjectLimitConfigRepository limitConfigRepository, ObjectMapper objectMapper) {
        this.limitConfigRepository = limitConfigRepository;
        this.objectMapper = objectMapper;
    }

    public List<LimitConfigDto> getLimits(Integer year) {
        int fiscalYear = (year != null) ? year : 2026;
        return limitConfigRepository.findByFiscalYearAndIsActiveTrue(fiscalYear).stream()
                .map(ProjectLimitConfig::toDto)
                .toList();
    }

    @Transactional
    public LimitConfigDto updateLimit(UUID id, UpdateLimitRequest request, UUID actorId) {
        ProjectLimitConfig limit = limitConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Limit tanımı bulunamadı: " + id));

        if (request.titleTr() != null && !request.titleTr().isBlank()) {
            limit.setTitleTr(request.titleTr().trim());
        }
        if (request.maxAmountTl() != null) {
            limit.setMaxAmountTl(request.maxAmountTl());
        }
        if (request.maxDurationMonths() != null) {
            limit.setMaxDurationMonths(request.maxDurationMonths());
        }
        if (request.vatIncluded() != null) {
            limit.setVatIncluded(request.vatIncluded());
        }
        if (request.conditionsJson() != null) {
            try {
                limit.setConditionsJson(objectMapper.writeValueAsString(request.conditionsJson()));
            } catch (JsonProcessingException e) {
                throw new BadRequestException("Geçersiz conditions_json formatı: " + e.getMessage());
            }
        }
        if (request.isActive() != null) {
            limit.setIsActive(request.isActive());
        }
        limit.setUpdatedBy(actorId);

        ProjectLimitConfig updated = limitConfigRepository.save(limit);
        return updated.toDto();
    }

    @Transactional
    public List<LimitConfigDto> cloneYearLimits(Integer sourceYear, Integer targetYear, UUID actorId) {
        if (sourceYear.equals(targetYear)) {
            throw new BadRequestException("Kaynak yıl ve hedef yıl aynı olamaz.");
        }

        List<ProjectLimitConfig> sourceLimits = limitConfigRepository.findByFiscalYear(sourceYear);
        if (sourceLimits.isEmpty()) {
            throw new BadRequestException(sourceYear + " mali yılına ait limit tanımı bulunamadı.");
        }

        List<ProjectLimitConfig> clonedList = new ArrayList<>();
        LocalDate effectiveDate = LocalDate.of(targetYear, 1, 1);

        for (ProjectLimitConfig src : sourceLimits) {
            // Check if already exists in target year
            if (limitConfigRepository.findByFiscalYearAndCode(targetYear, src.getCode()).isPresent()) {
                continue;
            }

            ProjectLimitConfig clone = new ProjectLimitConfig();
            clone.setFiscalYear(targetYear);
            clone.setRuleCategory(src.getRuleCategory());
            clone.setCode(src.getCode());
            clone.setTitleTr(src.getTitleTr());
            clone.setMaxAmountTl(src.getMaxAmountTl());
            clone.setMaxDurationMonths(src.getMaxDurationMonths());
            clone.setCurrency(src.getCurrency());
            clone.setVatIncluded(src.getVatIncluded());
            clone.setConditionsJson(src.getConditionsJson());
            clone.setEffectiveFrom(effectiveDate);
            clone.setIsActive(true);
            clone.setUpdatedBy(actorId);

            clonedList.add(limitConfigRepository.save(clone));
        }

        return clonedList.stream().map(ProjectLimitConfig::toDto).toList();
    }
}
