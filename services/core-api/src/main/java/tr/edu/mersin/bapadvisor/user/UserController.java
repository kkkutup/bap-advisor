package tr.edu.mersin.bapadvisor.user;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tr.edu.mersin.bapadvisor.common.UserRole;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COORDINATOR')")
    public ResponseEntity<PageUserResponse> listUsers(
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(userService.listUsers(role, pageRequest));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserProfileResponse response = userService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ResponseEntity.ok(userService.updateUser(id, request));
    }

    public record ToggleStatusRequest(@JsonProperty("is_active") boolean isActive) {}

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserProfileResponse> toggleStatus(
            @PathVariable UUID id,
            @RequestBody ToggleStatusRequest request
    ) {
        return ResponseEntity.ok(userService.toggleUserStatus(id, request.isActive()));
    }
}
