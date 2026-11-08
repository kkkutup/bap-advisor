package tr.edu.mersin.bapadvisor.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.data.domain.Page;

import java.util.List;

public record PageUserResponse(
        @JsonProperty("content") List<UserProfileResponse> content,
        @JsonProperty("total_elements") long totalElements,
        @JsonProperty("total_pages") int totalPages,
        @JsonProperty("number") int number,
        @JsonProperty("size") int size
) {
    public static PageUserResponse fromPage(Page<User> page) {
        List<UserProfileResponse> responses = page.getContent().stream()
                .map(UserProfileResponse::fromUser)
                .toList();
        return new PageUserResponse(
                responses,
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
