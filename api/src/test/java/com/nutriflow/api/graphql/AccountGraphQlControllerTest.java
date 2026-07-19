package com.nutriflow.api.graphql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutriflow.api.nutrition.NutritionTargetService;
import com.nutriflow.api.subscription.SubscriptionService;
import com.nutriflow.api.user.CreateUserCommand;
import com.nutriflow.api.user.UserEntity;
import com.nutriflow.api.user.UserRole;
import com.nutriflow.api.user.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.graphql.GraphQlTest;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.test.tester.GraphQlTester;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@GraphQlTest(AccountGraphQlController.class)
@Import({GraphQlScalarConfiguration.class, DomainExceptionResolver.class})
class AccountGraphQlControllerTest {

    @Autowired private GraphQlTester graphQlTester;

    @MockitoBean private UserService userService;
    @MockitoBean private SubscriptionService subscriptionService;
    @MockitoBean private NutritionTargetService nutritionTargetService;

    @Test
    void createUserBindsInputDelegatesToServiceAndSerializesDateTime() {
        UUID userId = UUID.randomUUID();
        UserEntity user =
                new UserEntity(
                        userId, "Ada", "ada@example.com", UserRole.CLIENT);
        ReflectionTestUtils.setField(
                user, "createdAt", Instant.parse("2026-07-19T12:30:00Z"));
        when(userService.create(any())).thenReturn(user);

        graphQlTester
                .document(
                        """
                        mutation CreateUser($input: CreateUserInput!) {
                          createUser(input: $input) {
                            id
                            name
                            email
                            role
                            createdAt
                          }
                        }
                        """)
                .variable(
                        "input",
                        Map.of(
                                "name", "Ada",
                                "email", "ADA@Example.com",
                                "role", "CLIENT"))
                .execute()
                .path("createUser.id")
                .entity(String.class)
                .isEqualTo(userId.toString())
                .path("createUser.createdAt")
                .entity(String.class)
                .satisfies(value -> assertThat(value).startsWith("2026-07-19T12:30"));

        ArgumentCaptor<CreateUserCommand> command =
                ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(userService).create(command.capture());
        assertThat(command.getValue().email()).isEqualTo("ADA@Example.com");
        assertThat(command.getValue().role()).isEqualTo(UserRole.CLIENT);
    }
}
