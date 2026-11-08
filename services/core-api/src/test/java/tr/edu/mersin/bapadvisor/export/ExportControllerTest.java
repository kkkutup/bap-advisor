package tr.edu.mersin.bapadvisor.export;

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
import tr.edu.mersin.bapadvisor.common.SubmissionType;
import tr.edu.mersin.bapadvisor.submission.CreateSubmissionRequest;
import tr.edu.mersin.bapadvisor.submission.SubmissionDetailResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String officerToken;
    private UUID submissionId;

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

        CreateSubmissionRequest createReq = new CreateSubmissionRequest(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Deniz Ekosisteminde Mikroplastik Analizi",
                "Prof. Dr. Deniz Aksoy",
                "Prof. Dr.",
                "Su Ürünleri Fakültesi",
                null
        );

        MvcResult createResult = mockMvc.perform(post("/api/v1/submissions")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        SubmissionDetailResponse detail = objectMapper.readValue(
                createResult.getResponse().getContentAsString(), SubmissionDetailResponse.class
        );
        this.submissionId = detail.id();

        MockMultipartFile file = new MockMultipartFile(
                "files", "Basvuru.pdf", "application/pdf", "%PDF sample file content".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/submissions/" + submissionId + "/versions")
                        .file(file)
                        .header("Authorization", officerToken))
                .andExpect(status().isCreated());
    }

    @Test
    void testExportPdf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/versions/1/export?format=pdf")
                        .header("Authorization", officerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdfBytes = result.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 100);
        String header = new String(pdfBytes, 0, 4);
        assertTrue("%PDF".equals(header));
    }

    @Test
    void testExportDocx() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/versions/1/export?format=docx")
                        .header("Authorization", officerToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                .andReturn();

        byte[] docxBytes = result.getResponse().getContentAsByteArray();
        assertTrue(docxBytes.length > 500);
        // ZIP magic bytes PK\x03\x04
        assertTrue(docxBytes[0] == 0x50 && docxBytes[1] == 0x4B);
    }
}
