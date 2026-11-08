package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

public record PageSubmissionResponse(
        @JsonProperty("content") List<SubmissionDetailResponse> content,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("number") int number,
        @JsonProperty("size") int size
) {
    public static PageSubmissionResponse fromPage(Page<SubmissionDetailResponse> page) {
        return new PageSubmissionResponse(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
