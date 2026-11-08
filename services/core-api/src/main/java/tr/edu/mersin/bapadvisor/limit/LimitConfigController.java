package tr.edu.mersin.bapadvisor.limit;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.auth.CustomUserDetailsService;
import tr.edu.mersin.bapadvisor.common.LimitConfigDto;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/limits")
public class LimitConfigController {

    private final LimitConfigService limitConfigService;

    public LimitConfigController(LimitConfigService limitConfigService) {
        this.limitConfigService = limitConfigService;
    }

    @GetMapping
    public ResponseEntity<List<LimitConfigDto>> getLimits(@RequestParam(required = false, defaultValue = "2026") Integer year) {
        return ResponseEntity.ok(limitConfigService.getLimits(year));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<LimitConfigDto> updateLimit(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLimitRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        return ResponseEntity.ok(limitConfigService.updateLimit(id, request, actorId));
    }

    @PostMapping("/clone-year")
    @PreAuthorize("hasAnyRole('COORDINATOR', 'ADMIN')")
    public ResponseEntity<List<LimitConfigDto>> cloneYear(
            @Valid @RequestBody CloneYearRequest request,
            @AuthenticationPrincipal CustomUserDetailsService.CustomUserDetails userDetails
    ) {
        UUID actorId = userDetails != null ? userDetails.getId() : null;
        List<LimitConfigDto> cloned = limitConfigService.cloneYearLimits(request.sourceYear(), request.targetYear(), actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(cloned);
    }
}
