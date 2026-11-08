package tr.edu.mersin.bapadvisor.qa;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.auth.CustomUserDetailsService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions/{id}/versions/{versionNo}/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping
    public ResponseEntity<QaAnswerResponse> askQuestion(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @Valid @RequestBody QaAskRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID userId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(qaService.askQuestion(id, versionNo, request.question(), userId));
    }
}
