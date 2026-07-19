package com.nutriflow.api.user;

import static com.nutriflow.api.common.DomainErrors.conflict;
import static com.nutriflow.api.common.DomainErrors.validation;

import java.util.Locale;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final UserRepository userRepository;

    @Transactional
    public UserEntity create(CreateUserCommand command) {
        if (command.name() == null || command.name().isBlank()) {
            throw validation("user.name", "Name is required");
        }
        if (command.email() == null
                || !EMAIL_PATTERN.matcher(command.email().trim()).matches()) {
            throw validation("user.email", "A valid email is required");
        }
        if (command.role() == null) {
            throw validation("user.role", "Role is required");
        }
        String normalizedEmail = command.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw conflict("user.email", "Email is already registered");
        }
        return userRepository.save(
                new UserEntity(null, command.name().trim(), normalizedEmail, command.role()));
    }
}
