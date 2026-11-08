package tr.edu.mersin.bapadvisor.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import tr.edu.mersin.bapadvisor.user.User;
import tr.edu.mersin.bapadvisor.user.UserRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Kullanıcı bulunamadı: " + email));
        return new CustomUserDetails(user);
    }

    public static class CustomUserDetails implements UserDetails {
        private final User user;

        public CustomUserDetails(User user) {
            this.user = user;
        }

        public User getUser() {
            return user;
        }

        public UUID getId() {
            return user.getId();
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
        }

        @Override
        public String getPassword() {
            return user.getPasswordHash();
        }

        @Override
        public String getUsername() {
            return user.getEmail();
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return user.isActive();
        }
    }
}
