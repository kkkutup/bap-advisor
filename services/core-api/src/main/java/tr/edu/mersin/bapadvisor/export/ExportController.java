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
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "audit") String type
    ) {
        String fmt = format.trim().toLowerCase();
        String reportType = type != null ? type.trim().toLowerCase() : "audit";
        boolean isRevision = "revision".equals(reportType);
        String filePrefix = isRevision ? "bap_revizyon_bildirim_raporu_v" : "bap_on_inceleme_tutanagi_v";

        if ("pdf".equals(fmt)) {
            byte[] pdfBytes = isRevision
                    ? exportService.exportRevisionPdf(id, versionNo)
                    : exportService.exportPdf(id, versionNo);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePrefix + versionNo + ".pdf\"")
                    .body(pdfBytes);
        } else if ("docx".equals(fmt)) {
            byte[] docxBytes = isRevision
                    ? exportService.exportRevisionDocx(id, versionNo)
                    : exportService.exportDocx(id, versionNo);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePrefix + versionNo + ".docx\"")
                    .body(docxBytes);
        } else {
            throw new BadRequestException("Desteklenmeyen rapor formatı: " + format + ". 'pdf' veya 'docx' olmalıdır.");
        }
    }
}
