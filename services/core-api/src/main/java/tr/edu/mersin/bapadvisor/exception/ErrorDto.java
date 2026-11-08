package tr.edu.mersin.bapadvisor.exception;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ErrorDto(
        @JsonProperty("message") String message,
        @JsonProperty("error_code") String errorCode,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("field_errors") List<FieldErrorItem> fieldErrors
) {
    public record FieldErrorItem(
            @JsonProperty("field") String field,
            @JsonProperty("message") String message
    ) {}

    public static ErrorDto of(String message, String errorCode) {
        return new ErrorDto(message, errorCode, Instant.now(), null);
    }

    public static ErrorDto of(String message, String errorCode, List<FieldErrorItem> fieldErrors) {
        return new ErrorDto(message, errorCode, Instant.now(), fieldErrors);
    }
}
