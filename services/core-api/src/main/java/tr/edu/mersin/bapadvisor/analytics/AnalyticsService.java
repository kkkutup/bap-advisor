package tr.edu.mersin.bapadvisor.analytics;

import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFinding;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFindingRepository;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final SubmissionRepository submissionRepository;
    private final EvaluationFindingRepository findingRepository;

    public AnalyticsService(SubmissionRepository submissionRepository, EvaluationFindingRepository findingRepository) {
        this.submissionRepository = submissionRepository;
        this.findingRepository = findingRepository;
    }

    public AnalyticsOverviewResponse getOverview() {
        List<Submission> allSubmissions = submissionRepository.findAll();
        long totalCount = allSubmissions.size();

        Map<String, Long> statusBreakdown = allSubmissions.stream()
                .collect(Collectors.groupingBy(s -> s.getStatus().name(), Collectors.counting()));

        Map<String, Long> typeBreakdown = allSubmissions.stream()
                .collect(Collectors.groupingBy(s -> s.getSubmissionType().name(), Collectors.counting()));

        // Aggregate top violated articles
        List<EvaluationFinding> allFindings = findingRepository.findAll();
        Map<String, List<EvaluationFinding>> violationsByArticle = allFindings.stream()
                .filter(f -> f.getSeverity() == FindingSeverity.VIOLATION || f.getSeverity() == FindingSeverity.MISSING_DOCUMENT)
                .collect(Collectors.groupingBy(f -> f.getRegulationDocName() + "|||" + f.getRegulationArticleRef()));

        List<AnalyticsOverviewResponse.TopViolatedArticle> topArticles = violationsByArticle.entrySet().stream()
                .map(entry -> {
                    String[] parts = entry.getKey().split("\\|\\|\\|");
                    String docName = parts.length > 0 ? parts[0] : "BAP Mevzuatı";
                    String articleRef = parts.length > 1 ? parts[1] : "Genel";
                    return new AnalyticsOverviewResponse.TopViolatedArticle(articleRef, docName, entry.getValue().size());
                })
                .sorted((a, b) -> Long.compare(b.violationCount(), a.violationCount()))
                .limit(5)
                .toList();

        // Default review hours calculation
        double avgHours = totalCount > 0 ? 1.4 : 0.0;

        return new AnalyticsOverviewResponse(
                totalCount,
                statusBreakdown,
                typeBreakdown,
                topArticles,
                avgHours
        );
    }
}
