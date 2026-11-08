package tr.edu.mersin.bapadvisor.submission;

import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tr.edu.mersin.bapadvisor.auth.CustomUserDetailsService;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions")
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping
    public ResponseEntity<PageSubmissionResponse> listSubmissions(
            @RequestParam(required = false) SubmissionStatus status,
            @RequestParam(required = false) SubmissionType submissionType,
            @RequestParam(required = false) String projectCode,
            @RequestParam(required = false) UUID assignedOfficerId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(submissionService.listSubmissions(
                status,
                submissionType,
                projectCode,
                assignedOfficerId,
                search,
                pageRequest
        ));
    }

    @PostMapping
    public ResponseEntity<SubmissionDetailResponse> createSubmission(
            @Valid @RequestBody CreateSubmissionRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID creatorId = userDetails != null ? userDetails.getId() : null;
        SubmissionDetailResponse created = submissionService.createSubmission(request, creatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionDetailResponse> getSubmissionById(@PathVariable UUID id) {
        return ResponseEntity.ok(submissionService.getSubmissionById(id));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SubmissionVersionResponse> createSubmissionVersion(
            @PathVariable UUID id,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            @RequestParam(value = "raw_text", required = false) String rawText,
            @RequestParam(value = "officer_notes", required = false) String officerNotes,
            @RequestParam(value = "enable_vision_fallback", required = false, defaultValue = "false") Boolean enableVisionFallback,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        SubmissionVersionResponse response = submissionService.createSubmissionVersion(
                id,
                files,
                rawText,
                officerNotes,
                enableVisionFallback,
                actorId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}/versions/{versionNo}")
    public ResponseEntity<SubmissionVersionResponse> getSubmissionVersion(
            @PathVariable UUID id,
            @PathVariable int versionNo
    ) {
        return ResponseEntity.ok(submissionService.getSubmissionVersion(id, versionNo));
    }

    @PatchMapping("/{id}/versions/{versionNo}/extracted-data")
    public ResponseEntity<SubmissionVersionResponse> updateExtractedData(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @RequestBody StructuredSubmissionDto dto,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(submissionService.updateExtractedData(id, versionNo, dto, actorId));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<SubmissionDetailResponse> updateSubmissionStatus(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(submissionService.updateSubmissionStatus(id, request, actorId));
    }
}
