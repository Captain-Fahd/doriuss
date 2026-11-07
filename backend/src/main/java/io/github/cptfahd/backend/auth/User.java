package io.github.cptfahd.backend.auth;


import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_username", columnList = "username")
        , @Index(name = "idx_users_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, name = "username", nullable = false)
    private String username;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(unique = true, name = "email", nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public User(String username, String email, int hash) {
    }

    @PrePersist
    private void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
        ensureLowercase();
    }

    private void ensureLowercase() {
        if (this.email != null) {
            this.email = this.email.toLowerCase();
        }

        if (this.username != null) {
            this.username = this.username.toLowerCase();
        }
    }

    @PreUpdate
    private void onUpdate() {
        ensureLowercase();
        this.updatedAt = OffsetDateTime.now();
    }

}
