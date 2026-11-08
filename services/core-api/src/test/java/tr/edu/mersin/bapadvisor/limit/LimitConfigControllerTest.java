package tr.edu.mersin.bapadvisor.limit;

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
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LimitConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        LoginRequest loginRequest = new LoginRequest("koordinator@mersin.edu.tr", "Koord123!");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse tokens = objectMapper.readValue(result.getResponse().getContentAsString(), AuthTokenResponse.class);
        this.coordinatorToken = "Bearer " + tokens.accessToken();
    }

    @Test
    void testLimits_Get_Update_CloneYear() throws Exception {
        // 1. Get 2026 limits
        MvcResult getResult = mockMvc.perform(get("/api/v1/limits?year=2026")
                        .header("Authorization", coordinatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[?(@.code == 'TP2')].max_amount_tl").value(hasItem(125000.0)))
                .andReturn();

        List<LimitConfigDto> list = objectMapper.readValue(
                getResult.getResponse().getContentAsString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, LimitConfigDto.class)
        );

        UUID tp2Id = list.stream().filter(l -> "TP2".equals(l.code())).findFirst().orElseThrow().id();

        // 2. Update TP2 limit
        UpdateLimitRequest updateReq = new UpdateLimitRequest(
                "Yüksek Lisans Tez Projeleri (Güncellendi)",
                BigDecimal.valueOf(135000.00),
                24,
                true,
                null,
                true
        );

        mockMvc.perform(put("/api/v1/limits/" + tp2Id)
                        .header("Authorization", coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.max_amount_tl").value(135000.00))
                .andExpect(jsonPath("$.title_tr").value("Yüksek Lisans Tez Projeleri (Güncellendi)"));

        // 3. Clone year (2026 -> 2027)
        CloneYearRequest cloneReq = new CloneYearRequest(2026, 2027);

        mockMvc.perform(post("/api/v1/limits/clone-year")
                        .header("Authorization", coordinatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cloneReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(5))))
                .andExpect(jsonPath("$[0].fiscal_year").value(2027));
    }
}
