package com.nutriflow.api.user;

public record CreateUserCommand(String name, String email, UserRole role) {}
