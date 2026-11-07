package io.github.cptfahd.backend.auth;

public sealed interface RegisterResult {
    record Success(UserResponse user) implements  RegisterResult {}
    record UsernameTaken() implements  RegisterResult {}
    record EmailTaken() implements RegisterResult {}
}
