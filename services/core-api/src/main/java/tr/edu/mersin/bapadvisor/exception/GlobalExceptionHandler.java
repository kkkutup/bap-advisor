package tr.edu.mersin.bapadvisor.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorDto> handleNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorDto.of(ex.getMessage(), "RESOURCE_NOT_FOUND"));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorDto> handleBadRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of(ex.getMessage(), "BAD_REQUEST"));
    }

    @ExceptionHandler({UnauthorizedException.class, BadCredentialsException.class})
    public ResponseEntity<ErrorDto> handleUnauthorized(Exception ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorDto.of(ex.getMessage(), "UNAUTHORIZED"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorDto.of("Bu işlem için yetkiniz bulunmamaktadır: " + ex.getMessage(), "ACCESS_DENIED"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<ErrorDto.FieldErrorItem> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(new ErrorDto.FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("İstek doğrulama hatası", "VALIDATION_ERROR", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGenericException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorDto.of("Sunucu hatası: " + ex.getMessage(), "INTERNAL_SERVER_ERROR"));
    }
}
