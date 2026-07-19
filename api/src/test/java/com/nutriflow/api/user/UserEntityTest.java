package com.nutriflow.api.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserEntityTest {

    @Test
    void normalizesEmailAndAssignsPersistenceDefaults() {
        UserEntity user =
                new UserEntity(null, "Ada Lovelace", "  ADA@Example.COM ", UserRole.CLIENT);

        user.prepareForInsert();

        assertThat(user.getId()).isNotNull();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("ada@example.com");
    }
}
