package tr.edu.mersin.bapadvisor.document;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions/{id}/versions/{versionNo}/documents")
public class DocumentController {

    private final SubmissionDocumentRepository documentRepository;
    private final DocumentStorageService storageService;

    public DocumentController(SubmissionDocumentRepository documentRepository, DocumentStorageService storageService) {
        this.documentRepository = documentRepository;
        this.storageService = storageService;
    }

    @GetMapping("/{docId}/download")
    public ResponseEntity<Resource> downloadDocument(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @PathVariable UUID docId
    ) {
        SubmissionDocument doc = documentRepository.findById(docId)
                .orElseThrow(() -> new ResourceNotFoundException("Belge bulunamadı: " + docId));

        Resource resource = storageService.loadFileAsResource(doc.getStoragePath());
        String contentType = doc.getMimeType() != null ? doc.getMimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                .body(resource);
    }
}
