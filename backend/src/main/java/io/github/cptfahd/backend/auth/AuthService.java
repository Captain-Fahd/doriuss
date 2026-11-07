package io.github.cptfahd.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterResult register(RegisterRequest request) {
        String username = request.username();
        String email = request.email();

        //check for the existence of the details previously
        if (userRepository.findByUsername(username).isPresent()) {
            return new RegisterResult.UsernameTaken();
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return new RegisterResult.EmailTaken();
        }

        User user = User.builder()
                .username(username)
                .displayName(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();

        userRepository.save(user);

        return new RegisterResult.Success(toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getCreatedAt());
    }
}
