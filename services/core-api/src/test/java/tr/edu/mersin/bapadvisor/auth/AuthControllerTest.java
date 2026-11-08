package tr.edu.mersin.bapadvisor.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testLoginSuccess_Admin() throws Exception {
        LoginRequest request = new LoginRequest("admin@mersin.edu.tr", "Admin123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andExpect(jsonPath("$.refresh_token", notNullValue()))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("admin@mersin.edu.tr"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void testLoginFailure_BadPassword() throws Exception {
        LoginRequest request = new LoginRequest("admin@mersin.edu.tr", "WrongPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error_code").value("UNAUTHORIZED"));
    }

    @Test
    void testRefreshToken_AndGetMe() throws Exception {
        // 1. Login to get refresh token
        LoginRequest loginRequest = new LoginRequest("memur@mersin.edu.tr", "Memur123!");
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        AuthTokenResponse authTokens = objectMapper.readValue(
                loginResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        // 2. Call /refresh
        RefreshTokenRequest refreshReq = new RefreshTokenRequest(authTokens.refreshToken());
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token", notNullValue()))
                .andReturn();

        AuthTokenResponse refreshedTokens = objectMapper.readValue(
                refreshResult.getResponse().getContentAsString(), AuthTokenResponse.class
        );

        // 3. Call /auth/me with new token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + refreshedTokens.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("memur@mersin.edu.tr"))
                .andExpect(jsonPath("$.role").value("OFFICER"));
    }
}
