package tr.edu.mersin.bapadvisor.evaluation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.auth.CustomUserDetailsService;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.submission.EvaluationModuleFlagsDto;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions/{id}")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @PostMapping("/versions/{versionNo}/evaluate")
    public ResponseEntity<EvaluationResultResponse> evaluate(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @RequestBody(required = false) EvaluationModuleFlagsDto moduleFlags,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        EvaluationResultResponse response = evaluationService.evaluate(id, versionNo, moduleFlags, actorId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/versions/{versionNo}/evaluation")
    public ResponseEntity<EvaluationResultResponse> getEvaluationResult(
            @PathVariable UUID id,
            @PathVariable int versionNo
    ) {
        return ResponseEntity.ok(evaluationService.getEvaluationResult(id, versionNo));
    }

    @PatchMapping("/findings/{findingId}/override")
    public ResponseEntity<FindingDto> overrideFinding(
            @PathVariable UUID id,
            @PathVariable UUID findingId,
            @Valid @RequestBody FindingOverrideRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        FindingDto updated = evaluationService.overrideFinding(id, findingId, request, actorId);
        return ResponseEntity.ok(updated);
    }
}
