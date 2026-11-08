package tr.edu.mersin.bapadvisor.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tr.edu.mersin.bapadvisor.common.DocumentRole;
import tr.edu.mersin.bapadvisor.common.StructuredSubmissionDto;
import tr.edu.mersin.bapadvisor.common.SubmissionType;
import tr.edu.mersin.bapadvisor.submission.SubmissionDetailResponse;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Bap2IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String validApiKey = "bap2_test_secret_api_key_meu_2026";

    @Test
    void testBap2ImportAndExport() throws Exception {
        StructuredSubmissionDto structuredData = new StructuredSubmissionDto(
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Prof. Dr. Ayşe Yurt",
                "Prof. Dr.",
                "Fen Fakültesi",
                "Kuantum Nokta Tabanlı Biyosensörler",
                BigDecimal.valueOf(124000.00),
                null,
                24,
                null,
                Collections.emptyList(),
                null,
                true,
                false,
                List.of(DocumentRole.APPLICATION_FORM),
                "BAP2 Entegrasyon Test Verisi"
        );

        Bap2ImportRequest importReq = new Bap2ImportRequest(
                "BAP2-SYS-99412",
                SubmissionType.NEW_PROJECT,
                "TP2",
                "Kuantum Nokta Tabanlı Biyosensörler",
                "Prof. Dr. Ayşe Yurt",
                "Prof. Dr.",
                "Fen Fakültesi",
                structuredData,
                Collections.emptyList()
        );

        // 1. Unauthorized without valid key
        mockMvc.perform(post("/api/v1/integrations/bap2/import")
                        .header("X-BAP2-API-KEY", "wrong_key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importReq)))
                .andExpect(status().isUnauthorized());

        // 2. Success with valid key
        MvcResult importResult = mockMvc.perform(post("/api/v1/integrations/bap2/import")
                        .header("X-BAP2-API-KEY", validApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(importReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.external_bap2_id").value("BAP2-SYS-99412"))
                .andReturn();

        SubmissionDetailResponse detail = objectMapper.readValue(
                importResult.getResponse().getContentAsString(), SubmissionDetailResponse.class
        );

        // 3. Export to bap2
        mockMvc.perform(get("/api/v1/integrations/bap2/export/" + detail.id())
                        .header("X-BAP2-API-KEY", validApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submission_id").value(detail.id().toString()))
                .andExpect(jsonPath("$.external_bap2_id").value("BAP2-SYS-99412"));
    }
}
