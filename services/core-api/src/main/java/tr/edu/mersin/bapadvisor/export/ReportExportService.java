package tr.edu.mersin.bapadvisor.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.common.BudgetLineItemDto;
import tr.edu.mersin.bapadvisor.common.FindingDto;
import tr.edu.mersin.bapadvisor.common.FindingSeverity;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.document.SubmissionDocument;
import tr.edu.mersin.bapadvisor.document.SubmissionDocumentRepository;
import tr.edu.mersin.bapadvisor.evaluation.Evaluation;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFinding;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationFindingRepository;
import tr.edu.mersin.bapadvisor.evaluation.EvaluationRepository;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;
import tr.edu.mersin.bapadvisor.submission.Submission;
import tr.edu.mersin.bapadvisor.submission.SubmissionRepository;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersion;
import tr.edu.mersin.bapadvisor.submission.SubmissionVersionRepository;
import tr.edu.mersin.bapadvisor.user.User;
import tr.edu.mersin.bapadvisor.user.UserRepository;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
public class ReportExportService {

    private static final Logger log = LoggerFactory.getLogger(ReportExportService.class);

    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final SubmissionDocumentRepository documentRepository;
    private final EvaluationRepository evaluationRepository;
    private final EvaluationFindingRepository findingRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ReportExportService(
            SubmissionRepository submissionRepository,
            SubmissionVersionRepository versionRepository,
            SubmissionDocumentRepository documentRepository,
            EvaluationRepository evaluationRepository,
            EvaluationFindingRepository findingRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper
    ) {
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.documentRepository = documentRepository;
        this.evaluationRepository = evaluationRepository;
        this.findingRepository = findingRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public record ExportData(
            Submission submission,
            SubmissionVersion version,
            StructuredSubmissionDto structuredData,
            List<SubmissionDocument> documents,
            Evaluation evaluation,
            List<FindingDto> findings,
            String officerName
    ) {}

    private ExportData collectData(UUID submissionId, int versionNo) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Başvuru bulunamadı: " + submissionId));

        SubmissionVersion version = versionRepository.findBySubmissionIdAndVersionNo(submissionId, versionNo)
                .orElseThrow(() -> new ResourceNotFoundException("Versiyon bulunamadı: v" + versionNo));

        List<SubmissionDocument> docs = documentRepository.findByVersionId(version.getId());

        Evaluation evaluation = evaluationRepository.findTopByVersionIdOrderByCompletedAtDesc(version.getId()).orElse(null);
        List<FindingDto> findings = evaluation != null
                ? findingRepository.findByEvaluationId(evaluation.getId()).stream().map(EvaluationFinding::toDto).toList()
                : Collections.emptyList();

        StructuredSubmissionDto structuredData = null;
        if (version.getExtractedMetadataJson() != null && !version.getExtractedMetadataJson().isBlank()) {
            try {
                structuredData = objectMapper.readValue(version.getExtractedMetadataJson(), StructuredSubmissionDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse metadata: {}", e.getMessage());
            }
        }

        String officerName = "BAP Koordinasyon Görevlisi";
        if (submission.getAssignedOfficerId() != null) {
            officerName = userRepository.findById(submission.getAssignedOfficerId())
                    .map(User::getFullName)
                    .orElse(officerName);
        }

        return new ExportData(submission, version, structuredData, docs, evaluation, findings, officerName);
    }

    public byte[] exportPdf(UUID submissionId, int versionNo) {
        ExportData data = collectData(submissionId, versionNo);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        com.lowagie.text.Document document = new com.lowagie.text.Document(PageSize.A4, 36, 36, 40, 40);
        try {
            PdfWriter.getInstance(document, baos);
            document.open();

            // Header Font
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Font.BOLD, new Color(15, 23, 42));
            Font subTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Font.BOLD, new Color(30, 41, 59));
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Font.BOLD, new Color(15, 23, 42));
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.BOLD, Color.BLACK);
            Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, Color.DARK_GRAY);

            // Title block
            Paragraph header = new Paragraph();
            header.setAlignment(Element.ALIGN_CENTER);
            header.add(new Paragraph("T.C.\nMERSİN ÜNİVERSİTESİ", titleFont));
            header.add(new Paragraph("BİLİMSEL ARAŞTIRMA PROJELERİ (BAP) KOORDİNASYON BİRİMİ", subTitleFont));
            header.add(new Paragraph("MEVZUAT UYGUNLUK VE ÖN İNCELEME TUTANAĞI\n\n", titleFont));
            document.add(header);

            // 1. Submission Details Table
            document.add(new Paragraph("1. BAŞVURU KİMLİK BİLGİLERİ", sectionFont));
            document.add(new Paragraph(" "));
            PdfPTable subTable = new PdfPTable(4);
            subTable.setWidthPercentage(100);
            subTable.setWidths(new float[]{25, 25, 25, 25});

            addCell(subTable, "Referans No:", boldFont, Color.LIGHT_GRAY);
            addCell(subTable, data.submission().getReferenceNo(), regularFont, Color.WHITE);
            addCell(subTable, "Başvuru Türü:", boldFont, Color.LIGHT_GRAY);
            addCell(subTable, data.submission().getSubmissionType().name(), regularFont, Color.WHITE);

            addCell(subTable, "Proje Kodu:", boldFont, Color.LIGHT_GRAY);
            addCell(subTable, data.submission().getProjectCode() != null ? data.submission().getProjectCode() : "-", regularFont, Color.WHITE);
            addCell(subTable, "Versiyon No:", boldFont, Color.LIGHT_GRAY);
            addCell(subTable, "v" + data.version().getVersionNo(), regularFont, Color.WHITE);

            addCell(subTable, "Yürütücü:", boldFont, Color.LIGHT_GRAY);
            String yürütücü = (data.submission().getApplicantTitle() != null ? data.submission().getApplicantTitle() + " " : "") + data.submission().getApplicantName();
            addCell(subTable, yürütücü, regularFont, Color.WHITE);
            addCell(subTable, "Bölüm/Birim:", boldFont, Color.LIGHT_GRAY);
            addCell(subTable, data.submission().getDepartment() != null ? data.submission().getDepartment() : "-", regularFont, Color.WHITE);

            addCell(subTable, "Proje Başlığı:", boldFont, Color.LIGHT_GRAY);
            PdfPCell titleCell = new PdfPCell(new Phrase(data.submission().getTitle(), regularFont));
            titleCell.setColspan(3);
            titleCell.setPadding(4);
            subTable.addCell(titleCell);

            document.add(subTable);
            document.add(new Paragraph(" "));

            // 2. Budget & Duration
            document.add(new Paragraph("2. BÜTÇE VE SÜRE TALEBİ DEĞERLENDİRMESİ", sectionFont));
            document.add(new Paragraph(" "));
            PdfPTable budgetTable = new PdfPTable(3);
            budgetTable.setWidthPercentage(100);
            budgetTable.setWidths(new float[]{40, 30, 30});

            addCell(budgetTable, "Talep Edilen Toplam Bütçe:", boldFont, Color.LIGHT_GRAY);
            String bgtStr = data.structuredData() != null && data.structuredData().requestedBudgetTotalTl() != null
                    ? String.format("%,.2f TL", data.structuredData().requestedBudgetTotalTl().doubleValue())
                    : "Belirtilmemiş";
            addCell(budgetTable, bgtStr, regularFont, Color.WHITE);
            addCell(budgetTable, "2026 Mevzuat Tavanı Kontrolü", regularFont, Color.WHITE);

            addCell(budgetTable, "Talep Edilen Proje Süresi:", boldFont, Color.LIGHT_GRAY);
            String durStr = data.structuredData() != null && data.structuredData().requestedDurationMonths() != null
                    ? data.structuredData().requestedDurationMonths() + " Ay"
                    : "Belirtilmemiş";
            addCell(budgetTable, durStr, regularFont, Color.WHITE);
            addCell(budgetTable, "Azami Süre: 36 Ay (Tez Hariç)", regularFont, Color.WHITE);

            document.add(budgetTable);
            document.add(new Paragraph(" "));

            // 3. Document Check Table
            document.add(new Paragraph("3. BELGE PAKETİ VE ISLAK İMZA KONTROLÜ", sectionFont));
            document.add(new Paragraph(" "));
            PdfPTable docTable = new PdfPTable(4);
            docTable.setWidthPercentage(100);
            docTable.setWidths(new float[]{40, 25, 20, 15});

            addCell(docTable, "Dosya Adı", boldFont, new Color(226, 232, 240));
            addCell(docTable, "Belge Rolü", boldFont, new Color(226, 232, 240));
            addCell(docTable, "İmza Tespiti", boldFont, new Color(226, 232, 240));
            addCell(docTable, "OCR Güven", boldFont, new Color(226, 232, 240));

            if (data.documents().isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("Henüz yüklenmiş belge bulunmamaktadır.", regularFont));
                emptyCell.setColspan(4);
                emptyCell.setPadding(4);
                docTable.addCell(emptyCell);
            } else {
                for (SubmissionDocument d : data.documents()) {
                    addCell(docTable, d.getFileName(), regularFont, Color.WHITE);
                    addCell(docTable, d.getDocRole().name(), regularFont, Color.WHITE);
                    addCell(docTable, Boolean.TRUE.equals(d.getHasSignatureDetected()) ? "MEVCUT" : "YOK", regularFont, Color.WHITE);
                    addCell(docTable, d.getOcrConfidence() != null ? String.format("%%%.1f", d.getOcrConfidence() * 100) : "-", regularFont, Color.WHITE);
                }
            }
            document.add(docTable);
            document.add(new Paragraph(" "));

            // 4. Compliance Findings
            document.add(new Paragraph("4. MEVZUAT UYGUNLUK DENETİM BULGULARI", sectionFont));
            document.add(new Paragraph(" "));
            PdfPTable findingTable = new PdfPTable(5);
            findingTable.setWidthPercentage(100);
            findingTable.setWidths(new float[]{20, 15, 30, 20, 15});

            addCell(findingTable, "Kural / Kod", boldFont, new Color(226, 232, 240));
            addCell(findingTable, "Kategori", boldFont, new Color(226, 232, 240));
            addCell(findingTable, "Açıklama / Bulgular", boldFont, new Color(226, 232, 240));
            addCell(findingTable, "Mevzuat Dayanağı", boldFont, new Color(226, 232, 240));
            addCell(findingTable, "Durum", boldFont, new Color(226, 232, 240));

            if (data.findings().isEmpty()) {
                PdfPCell emptyCell = new PdfPCell(new Phrase("Mevzuat ihlali veya eksiklik tespit edilmemiştir.", regularFont));
                emptyCell.setColspan(5);
                emptyCell.setPadding(4);
                findingTable.addCell(emptyCell);
            } else {
                for (FindingDto f : data.findings()) {
                    addCell(findingTable, f.ruleCode(), regularFont, Color.WHITE);
                    addCell(findingTable, f.category().name(), regularFont, Color.WHITE);
                    addCell(findingTable, f.titleTr() + ": " + f.explanationTr(), regularFont, Color.WHITE);
                    addCell(findingTable, f.regulationDocName() + " - " + f.regulationArticleRef(), regularFont, Color.WHITE);

                    Color statusBg = Color.WHITE;
                    if (f.severity() == FindingSeverity.COMPLIANT) statusBg = new Color(220, 252, 231);
                    else if (f.severity() == FindingSeverity.VIOLATION) statusBg = new Color(254, 226, 226);
                    else if (f.severity() == FindingSeverity.MISSING_DOCUMENT) statusBg = new Color(254, 243, 199);
                    else if (f.severity() == FindingSeverity.AMBIGUOUS_COMMISSION) statusBg = new Color(224, 231, 255);

                    addCell(findingTable, f.severity().name(), boldFont, statusBg);
                }
            }
            document.add(findingTable);
            document.add(new Paragraph(" "));

            // 5. Outcome & Decisions
            document.add(new Paragraph("5. ÖN İNCELEME KARARI VE SONUÇ DEĞERLENDİRMESİ", sectionFont));
            document.add(new Paragraph(" "));

            String overall = data.evaluation() != null ? data.evaluation().getOverallComplianceStatus() : "DEĞERLENDİRİLMEDİ";
            Paragraph outcomeP = new Paragraph("GENEL MEVZUAT UYGUNLUK DURUMU: " + overall + "\n", boldFont);
            document.add(outcomeP);

            if (data.evaluation() != null && data.evaluation().getRevisionDraftTr() != null) {
                document.add(new Paragraph("Eksik / İade Gerekçesi (Yürütücüye Bildirilecek Metin):", boldFont));
                document.add(new Paragraph(data.evaluation().getRevisionDraftTr(), regularFont));
                document.add(new Paragraph(" "));
            }

            if (data.evaluation() != null && data.evaluation().getCommissionReferralSummaryTr() != null) {
                document.add(new Paragraph("BAP Komisyonuna (Profesör Kurulu) Sevk Notu:", boldFont));
                document.add(new Paragraph(data.evaluation().getCommissionReferralSummaryTr(), regularFont));
                document.add(new Paragraph(" "));
            }

            // 6. Signature Block
            document.add(new Paragraph(" "));
            document.add(new Paragraph("6. İMZA VE ONAY BLOKU", sectionFont));
            document.add(new Paragraph(" "));

            PdfPTable signTable = new PdfPTable(2);
            signTable.setWidthPercentage(100);
            signTable.setWidths(new float[]{50, 50});

            PdfPCell sign1 = new PdfPCell();
            sign1.setBorder(Rectangle.NO_BORDER);
            sign1.addElement(new Paragraph("Ön İncelemeyi Yapan Görevli\n\n\n\n", regularFont));
            sign1.addElement(new Paragraph(data.officerName(), boldFont));
            sign1.addElement(new Paragraph("BAP Koordinasyon Birimi", regularFont));
            signTable.addCell(sign1);

            PdfPCell sign2 = new PdfPCell();
            sign2.setBorder(Rectangle.NO_BORDER);
            sign2.addElement(new Paragraph("BAP Koordinatörü Onayı\n\n\n\n", regularFont));
            sign2.addElement(new Paragraph("Prof. Dr. ............................", boldFont));
            sign2.addElement(new Paragraph("BAP Koordinatörü", regularFont));
            signTable.addCell(sign2);

            document.add(signTable);

            document.close();
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF tutanak üretilemedi: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    public byte[] exportDocx(UUID submissionId, int versionNo) {
        ExportData data = collectData(submissionId, versionNo);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (XWPFDocument doc = new XWPFDocument()) {
            // Title
            XWPFParagraph titlePara = doc.createParagraph();
            titlePara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun r1 = titlePara.createRun();
            r1.setBold(true);
            r1.setFontSize(14);
            r1.setText("T.C. MERSİN ÜNİVERSİTESİ");
            r1.addBreak();
            r1.setText("BİLİMSEL ARAŞTIRMA PROJELERİ (BAP) KOORDİNASYON BİRİMİ");
            r1.addBreak();
            r1.setText("MEVZUAT UYGUNLUK VE ÖN İNCELEME TUTANAĞI");
            r1.addBreak();
            r1.addBreak();

            // 1. Details
            XWPFParagraph p1 = doc.createParagraph();
            XWPFRun rP1 = p1.createRun();
            rP1.setBold(true);
            rP1.setText("1. BAŞVURU KİMLİK BİLGİLERİ");

            XWPFTable t1 = doc.createTable(4, 2);
            t1.getRow(0).getCell(0).setText("Referans No:");
            t1.getRow(0).getCell(1).setText(data.submission().getReferenceNo());
            t1.getRow(1).getCell(0).setText("Başvuru Türü:");
            t1.getRow(1).getCell(1).setText(data.submission().getSubmissionType().name());
            t1.getRow(2).getCell(0).setText("Yürütücü:");
            t1.getRow(2).getCell(1).setText(data.submission().getApplicantName());
            t1.getRow(3).getCell(0).setText("Proje Başlığı:");
            t1.getRow(3).getCell(1).setText(data.submission().getTitle());

            // 2. Budget & Findings
            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().addBreak();
            XWPFRun rP2 = p2.createRun();
            rP2.setBold(true);
            rP2.setText("2. MEVZUAT UYGUNLUK DENETİM BULGULARI");

            XWPFTable t2 = doc.createTable();
            XWPFTableRow headerRow = t2.getRow(0);
            headerRow.getCell(0).setText("Kural Kodu");
            headerRow.addNewTableCell().setText("Kategori");
            headerRow.addNewTableCell().setText("Mevzuat Maddesi");
            headerRow.addNewTableCell().setText("Açıklama");
            headerRow.addNewTableCell().setText("Durum");

            for (FindingDto f : data.findings()) {
                XWPFTableRow row = t2.createRow();
                row.getCell(0).setText(f.ruleCode());
                row.getCell(1).setText(f.category().name());
                row.getCell(2).setText(f.regulationDocName() + " - " + f.regulationArticleRef());
                row.getCell(3).setText(f.titleTr() + ": " + f.explanationTr());
                row.getCell(4).setText(f.severity().name());
            }

            // 3. Outcome
            XWPFParagraph p3 = doc.createParagraph();
            p3.createRun().addBreak();
            XWPFRun rP3 = p3.createRun();
            rP3.setBold(true);
            String overall = data.evaluation() != null ? data.evaluation().getOverallComplianceStatus() : "DEĞERLENDİRİLMEDİ";
            rP3.setText("3. ÖN İNCELEME KARARI: " + overall);

            if (data.evaluation() != null && data.evaluation().getRevisionDraftTr() != null) {
                XWPFParagraph pRev = doc.createParagraph();
                pRev.createRun().setText("İade/Revizyon Gerekçesi:\n" + data.evaluation().getRevisionDraftTr());
            }

            // 4. Signatures
            XWPFParagraph pSign = doc.createParagraph();
            pSign.createRun().addBreak();
            pSign.createRun().addBreak();
            XWPFRun rSign = pSign.createRun();
            rSign.setText("Ön İncelemeyi Yapan BAP Görevlisi: " + data.officerName() + "           BAP Koordinatörü Onayı");

            doc.write(baos);
        } catch (Exception e) {
            log.error("DOCX generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("DOCX tutanak üretilemedi: " + e.getMessage(), e);
        }

        return baos.toByteArray();
    }

    private void addCell(PdfPTable table, String text, Font font, Color bgColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "-", font));
        cell.setBackgroundColor(bgColor);
        cell.setPadding(4);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        table.addCell(cell);
    }
}
