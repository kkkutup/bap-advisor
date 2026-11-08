package tr.edu.mersin.bapadvisor.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.edu.mersin.bapadvisor.common.UserRole;
import tr.edu.mersin.bapadvisor.exception.BadRequestException;
import tr.edu.mersin.bapadvisor.exception.ResourceNotFoundException;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public PageUserResponse listUsers(UserRole role, Pageable pageable) {
        Page<User> page = role != null
                ? userRepository.findByRole(role, pageable)
                : userRepository.findAll(pageable);
        return PageUserResponse.fromPage(page);
    }

    @Transactional
    public UserProfileResponse createUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Bu e-posta adresine sahip bir kullanıcı zaten mevcut: " + email);
        }

        User user = new User(
                UUID.randomUUID(),
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.role()
        );

        User saved = userRepository.save(user);
        return UserProfileResponse.fromUser(saved);
    }

    @Transactional
    public UserProfileResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: " + id));

        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }

        User updated = userRepository.save(user);
        return UserProfileResponse.fromUser(updated);
    }

    @Transactional
    public UserProfileResponse toggleUserStatus(UUID id, boolean isActive) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Kullanıcı bulunamadı: " + id));

        user.setActive(isActive);
        User updated = userRepository.save(user);
        return UserProfileResponse.fromUser(updated);
    }
}
