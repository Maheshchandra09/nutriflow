package com.nutriflow.worker;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class MealPlanEventHandlerTest {

    @Test
    void handlerCanBeCreated() {
        assertNotNull(new MealPlanEventHandler());
    }
}

