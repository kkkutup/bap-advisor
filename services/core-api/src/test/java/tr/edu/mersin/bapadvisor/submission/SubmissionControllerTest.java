package tr.edu.mersin.bapadvisor.submission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tr.edu.mersin.bapadvisor.auth.AuthTokenResponse;
import tr.edu.mersin.bapadvisor.auth.LoginRequest;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionStatus;
import tr.edu.mersin.bapadvisor.common.SubmissionType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String officerToken;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest loginRequest = new LoginRequest("memur@mersin.edu.tr", "Memur123!");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse tokens = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        this.officerToken = "Bearer " + tokens.accessToken();
    }

    @Test
    void testSubmissionLifecycle_Create_Upload_Update_Status() throws Exception {
        // 1. Create submission
        CreateSubmissionRequest createReq = new CreateSubmissionRequest(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Akıllı Tarım ve Sensör Ağları BAP Projesi",
                "Doç. Dr. Ahmet Yılmaz",
                "Doç. Dr.",
                "Mühendislik Fakültesi",
                "EXT-BAP-2026-001"
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.reference_no", startsWith("BAP-2026-")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        SubmissionDetailResponse detail = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), SubmissionDetailResponse.class
        );
        UUID subId = detail.id();

        // 2. List submissions
        mockMvc.perform(get("/api/v1/submissions?status=DRAFT")
                        .header("Authorization", officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

        // 3. Get submission by ID
        mockMvc.perform(get("/api/v1/submissions/" + subId)
                        .header("Authorization", officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference_no").value(detail.referenceNo()));

        // 4. Upload version package (multipart)
        MockMultipartFile file1 = new MockMultipartFile(
                "files",
                "Basvuru_Formu.pdf",
                "application/pdf",
                "%PDF-1.4 sample content for BAP project application form".getBytes()
        );
        MockMultipartFile file2 = new MockMultipartFile(
                "files",
                "Proforma_Fatura_Analiz.pdf",
                "application/pdf",
                "%PDF-1.4 proforma invoice for laboratory consumable items".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/submissions/" + subId + "/versions")
                        .file(file1)
                        .file(file2)
                        .param("officer_notes", "İlk versiyon belgeleri sisteme aktarıldı.")
                        .header("Authorization", officerToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version_no").value(1))
                .andExpect(jsonPath("$.documents", hasSize(2)));

        // 5. Update extracted structured data
        StructuredSubmissionDto updatedData = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Doç. Dr. Ahmet Yılmaz",
                "Doç. Dr.",
                "Mühendislik Fakültesi",
                "Akıllı Tarım ve Sensör Ağları BAP Projesi",
                BigDecimal.valueOf(110000.00),
                null,
                24,
                null,
                Collections.emptyList(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM, DocumentRole.PROFORMA_INVOICE),
                "Memur tarafından OCR verisi doğrulandı."
        );

        mockMvc.perform(patch("/api/v1/submissions/" + subId + "/versions/1/extracted-data")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updatedData)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extracted_metadata.requested_budget_total_tl").value(110000.00))
                .andExpect(jsonPath("$.extracted_metadata.wet_signature_present").value(true));

        // 6. Transition status
        StatusTransitionRequest statusReq = new StatusTransitionRequest(
                SubmissionStatus.COMMISSION_REFERRED,
                "Şekli ve idari inceleme tamamlandı; BAP Komisyonuna sevk edildi.",
                Collections.emptyList()
        );

        mockMvc.perform(patch("/api/v1/submissions/" + subId + "/status")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMMISSION_REFERRED"));
    }
}
