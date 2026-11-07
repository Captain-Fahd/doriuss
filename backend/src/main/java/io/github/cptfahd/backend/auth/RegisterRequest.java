package io.github.cptfahd.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,
        @NotBlank @Email String email,
        @NotBlank @Size(max = 72) String password
) {
}
