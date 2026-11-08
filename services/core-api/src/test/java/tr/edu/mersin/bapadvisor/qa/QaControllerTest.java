package tr.edu.mersin.bapadvisor.qa;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tr.edu.mersin.bapadvisor.auth.AuthTokenResponse;
import tr.edu.mersin.bapadvisor.auth.LoginRequest;
import tr.edu.mersin.bapadvisor.common.SubmissionType;
import tr.edu.mersin.bapadvisor.submission.CreateSubmissionRequest;
import tr.edu.mersin.bapadvisor.submission.SubmissionDetailResponse;

import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QaControllerTest {

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
                "Robotik ve Yapay Görme Projesi",
                "Dr. Barış Aydın",
                "Dr. Öğr. Üyesi",
                "Mühendislik Fakültesi",
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
    }

    @Test
    void testAskQuestion() throws Exception {
        QaAskRequest askReq = new QaAskRequest(
                "Bu projede talep edilen dizüstü bilgisayar kalemi TP2 tez projesinde desteklenebilir mi?"
        );

        mockMvc.perform(post("/api/v1/submissions/" + submissionId + "/versions/1/qa")
                        .header("Authorization", officerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(askReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer_tr", notNullValue()))
                .andExpect(jsonPath("$.citations", notNullValue()));
    }
}
