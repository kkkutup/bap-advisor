package tr.edu.mersin.bapadvisor.evaluation;

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
import tr.edu.mersin.bapadvisor.submission.EvaluationModuleFlagsDto;
import tr.edu.mersin.bapadvisor.submission.SubmissionDetailResponse;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvaluationControllerTest {

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

        // Create a test submission and upload version 1
        CreateSubmissionRequest createReq = new CreateSubmissionRequest(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Yapay Zeka Destekli Biyomedikal Görüntü İşleme",
                "Doç. Dr. Selin Kaya",
                "Doç. Dr.",
                "Tıp Fakültesi",
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
                "files", "Form.pdf", "application/pdf", "%PDF test".getBytes()
        );
        mockMvc.perform(multipart("/api/v1/submissions/" + submissionId + "/versions")
                        .file(file)
                        .header("Authorization", officerToken))
                .andExpect(status().isCreated());
    }

    @Test
    void testEvaluationFlow_Evaluate_Get_Override() throws Exception {
        // 1. Run evaluation
        EvaluationModuleFlagsDto flags = new EvaluationModuleFlagsDto(true, true, true, null, null);

        MvcResult evalResult = mockMvc.perform(post("/api/v1/submissions/" + submissionId + "/versions/1/evaluate")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flags)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.overall_compliance_status", notNullValue()))
                .andExpect(jsonPath("$.findings", hasSize(greaterThanOrEqualTo(1))))
                .andReturn();

        EvaluationResultResponse evalResponse = objectMapper.readValue(
                evalResult.getResponse().getContentAsString(), EvaluationResultResponse.class
        );

        // 2. Get existing evaluation
        mockMvc.perform(get("/api/v1/submissions/" + submissionId + "/versions/1/evaluation")
                        .header("Authorization", officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(evalResponse.id().toString()))
                .andExpect(jsonPath("$.overall_compliance_status").value(evalResponse.overallComplianceStatus()));

        // 3. Override finding
        UUID findingId = evalResponse.findings().get(0).id();
        FindingOverrideRequest overrideReq = new FindingOverrideRequest(
                "APPROVED",
                "BAP Koordinatörü tarafından mevzuat istisnası kapsamında onaylandı."
        );

        mockMvc.perform(patch("/api/v1/submissions/" + submissionId + "/findings/" + findingId + "/override")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overrideReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.officer_override_status").value("APPROVED"))
                .andExpect(jsonPath("$.officer_override_reason").value("BAP Koordinatörü tarafından mevzuat istisnası kapsamında onaylandı."));
    }
}
