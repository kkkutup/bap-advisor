package tr.edu.mersin.bapadvisor.diff;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/submissions/{id}/diff")
public class DiffController {

    private final DiffService diffService;

    public DiffController(DiffService diffService) {
        this.diffService = diffService;
    }

    @GetMapping
    public ResponseEntity<VersionDiffResponse> getVersionDiff(
            @PathVariable UUID id,
            @RequestParam int fromVersion,
            @RequestParam int toVersion
    ) {
        return ResponseEntity.ok(diffService.calculateDiff(id, fromVersion, toVersion));
    }
}
