package tr.edu.mersin.bapadvisor.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

public record AnalyticsOverviewResponse(
        @JsonProperty("total_submissions_count") long totalSubmissionsCount,
        @JsonProperty("status_breakdown") Map<String, Long> statusBreakdown,
        @JsonProperty("submission_type_breakdown") Map<String, Long> submissionTypeBreakdown,
        @JsonProperty("top_violated_articles") List<TopViolatedArticle> topViolatedArticles,
        @JsonProperty("average_review_hours") double averageReviewHours
) {
    public record TopViolatedArticle(
            @JsonProperty("article_ref") String articleRef,
            @JsonProperty("doc_name") String docName,
            @JsonProperty("violation_count") long violationCount
    ) {}
}
