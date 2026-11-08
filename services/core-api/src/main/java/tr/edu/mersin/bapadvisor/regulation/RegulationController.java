package tr.edu.mersin.bapadvisor.regulation;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/regulations")
public class RegulationController {

    private final RegulationService regulationService;

    public RegulationController(RegulationService regulationService) {
        this.regulationService = regulationService;
    }

    @GetMapping
    public ResponseEntity<List<RegulationDocumentDto>> listRegulations() {
        return ResponseEntity.ok(regulationService.listRegulations());
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<RegulationDocumentDto> uploadRegulation(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "slug", required = false) String slug,
            @RequestParam(value = "title_tr", required = false) String titleTr,
            @RequestParam(value = "doc_type", required = false, defaultValue = "YONERGE") String docType,
            @RequestParam(value = "version_label", required = false) String versionLabel
    ) {
        RegulationDocumentDto dto = regulationService.uploadRegulation(file, slug, titleTr, docType, versionLabel);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    public record ToggleRegulationRequest(@JsonProperty("is_active") boolean isActive) {}

    @PatchMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<RegulationDocumentDto> toggleRegulation(
            @PathVariable UUID id,
            @RequestBody ToggleRegulationRequest request
    ) {
        return ResponseEntity.ok(regulationService.toggleRegulation(id, request.isActive()));
    }

    @PostMapping("/sync-seed")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<List<RegulationDocumentDto>> syncSeed() {
        return ResponseEntity.ok(regulationService.syncSeedRegulations());
    }
}
