package tr.edu.mersin.bapadvisor.auth;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.exception.UnauthorizedException;
import tr.edu.mersin.bapadvisor.user.User;
import tr.edu.mersin.bapadvisor.user.UserProfileResponse;
import tr.edu.mersin.bapadvisor.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    public AuthTokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Geçersiz e-posta veya şifre."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Kullanıcı hesabı devre dışı bırakılmıştır.");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Geçersiz e-posta veya şifre.");
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        return AuthTokenResponse.of(
                accessToken,
                refreshToken,
                tokenProvider.getAccessTokenExpiresInSeconds(),
                UserProfileResponse.fromUser(user)
        );
    }

    public AuthTokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken();
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new UnauthorizedException("Geçersiz veya süresi dolmuş yenileme anahtarı.");
        }

        String email = tokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Kullanıcı hesabı devre dışı bırakılmıştır.");
        }

        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);

        return AuthTokenResponse.of(
                newAccessToken,
                newRefreshToken,
                tokenProvider.getAccessTokenExpiresInSeconds(),
                UserProfileResponse.fromUser(user)
        );
    }

    public UserProfileResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Kullanıcı bulunamadı: " + email));
        return UserProfileResponse.fromUser(user);
    }
}
