package tr.edu.mersin.bapadvisor.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.client.AiEngineClient;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/providers")
public class AdminProviderController {

    private final AiEngineClient aiEngineClient;

    public AdminProviderController(AiEngineClient aiEngineClient) {
        this.aiEngineClient = aiEngineClient;
    }

    @GetMapping("/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AiEngineClient.ProviderStatus> getProviderStatus() {
        return ResponseEntity.ok(aiEngineClient.getProviderStatus());
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<AiEngineClient.ProviderStatus> updateProviderConfig(@RequestBody Map<String, Object> config) {
        return ResponseEntity.ok(aiEngineClient.updateProviderConfig(config));
    }
}
