package tr.edu.mersin.bapadvisor.integration;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.config.AppProperties;
import tr.edu.mersin.bapadvisor.exception.UnauthorizedException;
import tr.edu.mersin.bapadvisor.submission.SubmissionDetailResponse;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/integrations/bap2")
public class Bap2IntegrationController {

    private final Bap2Service bap2Service;
    private final String expectedApiKey;

    public Bap2IntegrationController(Bap2Service bap2Service, AppProperties appProperties) {
        this.bap2Service = bap2Service;
        this.expectedApiKey = appProperties.getSecurity().getBap2ApiKey();
    }

    private void validateApiKey(String headerApiKey) {
        if (headerApiKey == null || !headerApiKey.equals(expectedApiKey)) {
            throw new UnauthorizedException("Geçersiz X-BAP2-API-KEY başlığı.");
        }
    }

    @PostMapping("/import")
    public ResponseEntity<SubmissionDetailResponse> importBap2Submission(
            @RequestHeader(value = "X-BAP2-API-KEY", required = false) String apiKey,
            @Valid @RequestBody Bap2ImportRequest request
    ) {
        validateApiKey(apiKey);
        SubmissionDetailResponse imported = bap2Service.importBap2Submission(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }

    @GetMapping("/export/{submissionId}")
    public ResponseEntity<Bap2ExportResponse> exportBap2Evaluation(
            @RequestHeader(value = "X-BAP2-API-KEY", required = false) String apiKey,
            @PathVariable UUID submissionId
    ) {
        validateApiKey(apiKey);
        return ResponseEntity.ok(bap2Service.exportBap2Evaluation(submissionId));
    }
}
