package tr.edu.mersin.bapadvisor.export;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.exception.BadRequestException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions/{id}/versions/{versionNo}/export")
public class ExportController {

    private final ReportExportService exportService;

    public ExportController(ReportExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<byte[]> exportReport(
            @PathVariable UUID id,
            @PathVariable int versionNo,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        String fmt = format.trim().toLowerCase();

        if ("pdf".equals(fmt)) {
            byte[] pdfBytes = exportService.exportPdf(id, versionNo);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bap_on_inceleme_tutanagi_v" + versionNo + ".pdf\"")
                    .body(pdfBytes);
        } else if ("docx".equals(fmt)) {
            byte[] docxBytes = exportService.exportDocx(id, versionNo);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bap_on_inceleme_tutanagi_v" + versionNo + ".docx\"")
                    .body(docxBytes);
        } else {
            throw new BadRequestException("Desteklenmeyen rapor formatı: " + format + ". 'pdf' veya 'docx' olmalıdır.");
        }
    }
}
