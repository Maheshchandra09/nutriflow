package com.nutriflow.api.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.nutriflow.api.common.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository repository;

    @Test
    void createsUserWithCanonicalEmail() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserService service = new UserService(repository);

        UserEntity user =
                service.create(
                        new CreateUserCommand(
                                " Client ", " CLIENT@Example.COM ", UserRole.CLIENT));
        user.prepareForInsert();

        assertThat(user.getName()).isEqualTo("Client");
        assertThat(user.getEmail()).isEqualTo("client@example.com");
    }

    @Test
    void rejectsDuplicateCanonicalEmail() {
        when(repository.existsByEmail("client@example.com")).thenReturn(true);
        UserService service = new UserService(repository);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreateUserCommand(
                                                "Client",
                                                "CLIENT@example.com",
                                                UserRole.CLIENT)))
                .isInstanceOf(DomainException.class)
                .extracting("code")
                .isEqualTo("CONFLICT");
    }
}
