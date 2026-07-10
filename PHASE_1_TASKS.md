# Phase 1 — Auth & Accounts: Detailed Task Breakdown

## Context

Phase 0 is complete: the backend serves `GET /api/v1/ping` on port 8000, and the Flutter Windows client calls it and displays the result. Phase 1 adds user registration, login, and persistent sessions. This document expands each Phase 1 task into a plain-English description of what is required, the key decision that makes it non-trivial, and specific learning resources. No code is included.

**Starting point:**
- Backend: Spring Boot **4.1.0**, Java 21, Maven. `pom.xml` currently has only `spring-boot-starter-webmvc`, `devtools`, and the test starter.
- Client: Flutter (Dart 3.12.2), `ChangeNotifier`-based MVVM, only `http: ^1.6.0` as a dependency.

**One architectural note before starting:** the `IMPLEMENTATION_PLAN.md` recommends Riverpod. The current client uses `ChangeNotifier`. This plan continues with `ChangeNotifier` for Phase 1 — it is already established and Riverpod is a larger conceptual addition that would be introduced alongside entirely new screens. Introduce Riverpod in Phase 2 after the auth flow works. Add `provider` (first-party, lightweight) as a companion to `ChangeNotifier` for this phase.

---

## Recommended Implementation Order

Tasks are grouped below by concern. The order to *implement* them is:

**B1 → B2 → B3 → B4 + B5 → B6 → B7 + B10 → B8 → B9 → B11**
*(verify the whole backend with curl/Postman before touching Flutter)*
**F1 → F2 → F3 + F7 → F4 → F5 + F6 → F8**

---

## Backend Tasks

---

### B1. Add Maven Dependencies

**What it requires.**
Open `pom.xml` and add five groups of new starters/libraries. Spring Security provides the filter chain, authentication manager, and password encoding. Spring Data JPA provides the ORM layer so you write repository interfaces instead of raw SQL. The PostgreSQL JDBC driver is the runtime connector between JPA and the database. Flyway adds versioned schema migration. Spring Validation adds annotation-driven input validation (`@NotBlank`, `@Email`, etc.). Finally, a JWT library: **jjwt** (`io.jsonwebtoken`) is the most widely taught choice in the Spring ecosystem and has a fluent builder API suited to a first JWT implementation.

**Key decision.**
Spring Boot 4.1.0 ships with Spring Security **7.x**. The Security 6/7 API broke significantly from 5.x: `WebSecurityConfigurerAdapter` was removed and must not be used. Every Baeldung or Stack Overflow article written before mid-2022 shows the old style. Always check the publication date of any Spring Security tutorial before following it. Favour the official Spring Security reference docs over third-party tutorials.

**Resources.**
- Spring Boot 4.x managed dependency coordinates (version reference): https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- JJWT library README (the authoritative how-to for this library): https://github.com/jjwt/jjwt#readme
- Baeldung JJWT setup guide (verify the Spring Boot version used matches yours): https://www.baeldung.com/java-json-web-tokens-jjwt

---

### B2. PostgreSQL + Docker Compose Setup

**What it requires.**
You need PostgreSQL running locally before any JPA or Flyway code will start. Add a `docker-compose.yml` at the repo root defining one service — the official `postgres` image — with environment variables for `POSTGRES_USER`, `POSTGRES_PASSWORD`, and `POSTGRES_DB`. In `application.properties` add four properties: the JDBC URL (`jdbc:postgresql://localhost:5432/<yourdb>`), username, password, and optionally the driver class name (Spring Boot auto-detects it from the dependency, but being explicit avoids surprises). Spring Boot auto-configures HikariCP as the connection pool; you do not need to configure it manually at this stage, but knowing it exists and what it does (pre-opens a pool of connections instead of one per request) is useful context.

**Key decision.**
Never hard-code the database password in `application.properties`. Even in a solo project you will push this to GitHub. Use environment variable placeholders (`${DB_PASSWORD}`) and keep a local `.env` or `.env.local` file excluded by `.gitignore`. Establishing this hygiene now means Phase 6 (real deployment, real secrets) requires no rework.

**Resources.**
- Spring Boot SQL data access auto-configuration: https://docs.spring.io/spring-boot/reference/data/sql.html
- Docker Compose getting started (official): https://docs.docker.com/compose/gettingstarted/
- HikariCP configuration reference (understand what the defaults mean): https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby

---

### B3. Flyway Migration — users Table

**What it requires.**
Flyway is a schema migration tool that tracks your DDL changes as a numbered series of SQL files. When the Spring Boot app starts, Flyway runs any files it has not yet executed, in order, and records which ones it has run in a `flyway_schema_history` table. The naming convention is strict: `V{number}__{description}.sql` (two underscores), stored in `src/main/resources/db/migration/`. Your first file, `V1__create_users_table.sql`, defines the users table. It needs at minimum: a UUID primary key defaulting to `gen_random_uuid()`, `username` and `email` as unique non-null VARCHAR columns, a `password_hash` column (never `password`), and `created_at` / `updated_at` TIMESTAMPTZ columns with `DEFAULT now()`. Using `TIMESTAMPTZ` (timezone-aware) rather than `TIMESTAMP` matters when clients are in different timezones.

**Key decision.**
Why Flyway instead of `spring.jpa.hibernate.ddl-auto=create`? The Hibernate DDL modes are for development convenience only. They cannot safely alter a table that already contains real data, they provide no rollback path, and they leave no audit trail. Once a friend registers an account, `ddl-auto=create` will wipe their row on the next restart. Flyway's `V1__`, `V2__`, `V3__` sequence is how every production application manages schema evolution. Establish this from the beginning.

**Resources.**
- Flyway official docs — naming and migration concepts: https://documentation.red-gate.com/fd/migrations-184127470.html
- Baeldung Flyway with Spring Boot: https://www.baeldung.com/database-migrations-with-flyway
- PostgreSQL `gen_random_uuid()` (requires PostgreSQL 13+ or the `pgcrypto` extension): https://www.postgresql.org/docs/current/functions-uuid.html

---

### B4. User JPA Entity

**What it requires.**
A JPA entity is a Java class annotated with `@Entity` that maps to a database table. Each field maps to a column. You need: `@Id` on the primary key, `@GeneratedValue` configured to delegate UUID generation to the database, `@Column(unique = true, nullable = false)` on `email` and `username`, and lifecycle callbacks (`@PrePersist` / `@PreUpdate`) to automatically set `createdAt` and `updatedAt` before insert and update. The field storing the password is named `passwordHash` — never `password` — to make the naming intent visible everywhere the class is read. Following your existing `PingController` convention of using Java records, you may model lightweight response shapes as records, but the JPA entity itself should be a regular class (records have immutability constraints that conflict with Hibernate's entity lifecycle requirements).

**Key decision.**
UUID vs Long as primary key is a real trade-off. A `BIGSERIAL` (auto-incrementing Long) is faster to index and simpler to debug. A UUID is globally unique without coordination, makes client-generated IDs safe in future, and does not expose your record count to API consumers. For a Discord-style app where IDs appear in API responses and URLs, UUID is the right call — it is what your `IMPLEMENTATION_PLAN.md` specifies and what Discord itself uses.

**Resources.**
- Spring Data JPA entity mapping reference: https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
- Baeldung — JPA entity lifecycle events (`@PrePersist` / `@PreUpdate`): https://www.baeldung.com/jpa-entity-lifecycle-events
- Hibernate 7 migration guide (Hibernate 7 ships in Spring Boot 4.x — check what changed): https://docs.jboss.org/hibernate/orm/7.0/migration-guide/migration-guide.html

---

### B5. UserRepository

**What it requires.**
Spring Data JPA generates a complete CRUD implementation at startup from an interface that extends `JpaRepository<User, UUID>`. You get `save()`, `findById()`, `deleteById()`, and `findAll()` for free. For auth you additionally need: `Optional<User> findByEmail(String email)` (used at login to look up the user), `boolean existsByUsername(String username)`, and `boolean existsByEmail(String email)` (used at registration to check uniqueness before the database constraint fires, giving a clean error message instead of a constraint violation exception). Spring derives these queries from the method name — no SQL or `@Query` annotation is needed.

**Key decision.**
Spring derives queries by parsing the method name at startup. If you misspell `findByEmial`, it does not fail to compile — it fails to start the application with a `PropertyReferenceException`. This is confusing the first time. The derived query syntax must match entity field names exactly (case-sensitive after `findBy`). Read the reference docs to understand what the parser accepts before writing these methods.

**Resources.**
- Spring Data JPA reference — derived query methods: https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html
- Baeldung introduction to Spring Data JPA: https://www.baeldung.com/the-persistence-layer-with-spring-data-jpa

---

### B6. Password Hashing with BCrypt

**What it requires.**
You never store the user's actual password. You store a hash — the output of a one-way function that cannot be reversed. At login, you hash what the user typed and compare it to the stored hash. BCrypt is the standard choice in the Spring ecosystem, available as `BCryptPasswordEncoder` in `spring-security-crypto` (included transitively with `spring-boot-starter-security`). Declare it as a `@Bean` so it can be injected wherever needed. BCrypt automatically includes a random salt in the output, so two users with the same password produce different hashes, making rainbow-table attacks useless.

**Key decision.**
BCrypt's "work factor" (also called cost or strength) determines how many rounds of computation are performed. The default is 10 (2^10 = 1024 rounds), which takes roughly 100–200ms on modern hardware. Higher is slower for attackers (good) but also slower for your server at login. Do not go below 10. Do not use MD5, SHA-1, or SHA-256 for password storage — they are fast by design, which is exactly the wrong property for a password hash.

**Resources.**
- Spring Security — password storage and `PasswordEncoder`: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- Baeldung — Spring Security BCrypt encoding: https://www.baeldung.com/spring-security-registration-password-encoding-bcrypt
- Auth0 — why BCrypt (conceptual background): https://auth0.com/blog/hashing-in-action-understanding-bcrypt/

---

### B7. Register Endpoint

**What it requires.**
`POST /api/v1/auth/register` accepts a JSON body with `username`, `email`, and `password`. It must: validate that none of the fields are blank (using `@Valid` and constraint annotations on the request DTO), check that the username and email are not already taken (B5 repository methods), hash the password (B6 encoder), save the new `User` entity, and return a response. The response should include `id`, `username`, `email`, and `createdAt` — never `passwordHash`. Return HTTP 201 Created on success. This endpoint must also have a global exception handler (`@RestControllerAdvice`) that catches `MethodArgumentNotValidException` and returns a structured JSON error body (e.g., `{ "errors": { "email": "must not be blank" } }`) rather than Spring's default verbose 400.

**Key decision.**
The global exception handler (`@RestControllerAdvice`) is not optional — it is required for the client to display useful error messages. This is also the right time to build it because it recurs across every endpoint in the project. A clean, consistent error shape now means every future feature inherits it for free.

**Resources.**
- Spring MVC validation reference: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
- Baeldung — Bean Validation in Spring Boot: https://www.baeldung.com/spring-boot-bean-validation
- Baeldung — global exception handling with `@ControllerAdvice`: https://www.baeldung.com/exception-handling-for-rest-with-spring

---

### B8. JWT Fundamentals

**What it requires.**
A JSON Web Token is a compact string with three Base64URL-encoded segments separated by dots: `header.payload.signature`. The header declares the token type and signing algorithm. The payload contains "claims" — key-value pairs about the subject. The essential claims for your use case: `sub` (subject = the user's UUID as a string), `iat` (issued at, Unix timestamp), and `exp` (expiration, Unix timestamp). The signature is computed with HMAC-SHA256 (HS256) over the header and payload using a secret key known only to the server. This is how the server verifies an incoming token has not been tampered with — it recomputes the HMAC and compares. The secret key must be at least 32 bytes (256 bits) and must live in environment config, never in source code. Build a `JwtUtil` (or `JwtService`) class with two methods: `generateToken(userId)` and `parseToken(tokenString)` (which returns the claims or throws on invalid/expired tokens).

**Key decision.**
HS256 (symmetric, one secret key) vs RS256 (asymmetric, private key signs, public key verifies). RS256 is needed only when multiple independent services need to verify tokens without sharing the signing secret. For a single backend, HS256 is simpler with no loss. Token lifetime: a 7-day access token is a pragmatic starting point for Phase 1. The industry pattern of short-lived access tokens + long-lived refresh tokens is correct but adds significant complexity to both backend and client. Introduce refresh tokens in Phase 6 when you harden the app.

**Resources.**
- JWT.io — conceptual introduction and interactive decoder: https://jwt.io/introduction
- RFC 7519 — the JWT specification (the authoritative reference for claim names): https://datatracker.ietf.org/doc/html/rfc7519
- JJWT library README — building and parsing tokens: https://github.com/jjwt/jjwt#readme

---

### B9. Login Endpoint

**What it requires.**
`POST /api/v1/auth/login` accepts a JSON body with `email` and `password`. It must: look up the user by email (return 401 if not found — not 404, because distinguishing "email unknown" from "wrong password" helps attackers enumerate accounts), verify the password using `BCryptPasswordEncoder.matches()`, and if valid, build and sign a JWT with the user's UUID as the subject (using B8's `JwtUtil`). Return 200 OK with a JSON body containing at minimum the `token` string; optionally also return `id`, `username`, and `email` so the client does not need a separate profile request.

**Key decision.**
Whether to use Spring's `AuthenticationManager` or do the lookup and `matches()` call manually. The `AuthenticationManager` approach calls `UserDetailsService.loadUserByUsername()` and the password encoder internally; if credentials are wrong it throws `BadCredentialsException`. This is more idiomatic and means Spring's security hooks (brute-force protection, audit events) apply automatically. The manual approach is simpler to understand at first. Either works for Phase 1; the `AuthenticationManager` approach is worth learning since it connects directly to how B10 and B11 work.

**Resources.**
- Spring Security — `AuthenticationManager` and `AuthenticationProvider` architecture: https://docs.spring.io/spring-security/reference/servlet/authentication/architecture.html
- Baeldung — Spring Security login (current SecurityFilterChain style): https://www.baeldung.com/spring-security-login
- Baeldung — `UserDetailsService` implementation: https://www.baeldung.com/spring-security-authentication-with-a-database

---

### B10. Spring Security Filter Chain

**What it requires.**
The `SecurityFilterChain` bean is the central configuration object for Spring Security. It must configure: route authorization rules (permit `/api/v1/auth/**` without a token; require authentication for all other routes), session policy set to `STATELESS` (JWTs are stateless — no server-side session should be created or consulted), CSRF disabled (CSRF attacks exploit browser cookies; since you use Bearer tokens in headers, not cookies, this protection is inapplicable and would block your Flutter client), and CORS rules allowing your Flutter app's origin. Also in this bean, register your `JwtAuthenticationFilter` (B11) with `.addFilterBefore(...)`.

**Key decision.**
CORS is often the most confusing part of this step. It is enforced by *browsers*, not by the Flutter Windows client — so you will not hit CORS errors in Phase 1. However, configure CORS correctly now (on the `SecurityFilterChain`, not just Spring MVC), because preflight `OPTIONS` requests get rejected by Security before they reach Spring MVC's CORS handler if you do it wrong. When you eventually add a web client or test from a browser-based tool, it will work without revisiting this.

**Resources.**
- Spring Security reference — `SecurityFilterChain` and `HttpSecurity` configuration: https://docs.spring.io/spring-security/reference/servlet/configuration/java.html
- Spring Security reference — CORS integration: https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html
- Baeldung — stateless REST with Spring Security: https://www.baeldung.com/spring-security-session

---

### B11. JWT Authentication Filter

**What it requires.**
This is the filter that validates incoming JWTs on every protected request. Implement it by extending `OncePerRequestFilter`, which guarantees exactly one execution per HTTP request. The logic: extract the `Authorization` header, check it starts with `Bearer `, take the remainder as the token string, call `JwtUtil.parseToken()` to validate it (JJWT throws typed exceptions for expired, malformed, or invalid-signature tokens), extract the `sub` claim (the user UUID), load `UserDetails` from the database for that UUID, and set the `SecurityContext` with a populated `Authentication` object. After this filter runs, Spring Security's authorization layer reads from the `SecurityContext` to decide whether to allow the request. Register this filter before `UsernamePasswordAuthenticationFilter` in the `SecurityFilterChain`.

**Key decision.**
What happens when the token is invalid. If you let the exception propagate out of the filter, Spring returns a 500 Internal Server Error — wrong. Catch JWT-specific exceptions inside the filter and set the response status to 401, or delegate to a configured `AuthenticationEntryPoint` on the `SecurityFilterChain` that always returns a clean 401. The entry point approach also handles the case where no `Authorization` header is present at all, so it covers more scenarios with less code.

**Resources.**
- Spring Security reference — the filter chain and `OncePerRequestFilter`: https://docs.spring.io/spring-security/reference/servlet/architecture.html#servlet-filters-review
- Baeldung — Spring Security with JWT filter: https://www.baeldung.com/spring-security-oauth-jwt
- JJWT exception types (what to catch): https://github.com/jjwt/jjwt#jws-exceptions

---

## Flutter Client Tasks

---

### F1. New Dependencies

**What it requires.**
Add three packages to `pubspec.yaml`. `flutter_secure_storage` stores key-value pairs in the platform's credential vault (Windows Credential Manager on Windows, Keychain on iOS, Android Keystore on Android) — this is where the JWT will live after login. `go_router` provides declarative navigation with route guard support (needed for F8). `provider` (first-party, Flutter team) is the `ChangeNotifier` integration layer that makes the `AuthViewModel` accessible anywhere in the widget tree without passing it through constructors.

**Key decision.**
`flutter_secure_storage` requires platform-specific setup steps that go beyond `flutter pub add`. On Windows it requires a minimum target version and a CMakeLists.txt entry. Read the "Getting started" section on the pub.dev page for each platform you support before assuming it works. Skipping these setup steps is the most common cause of "compiles but crashes at runtime" with this package.

**Resources.**
- `flutter_secure_storage` on pub.dev (read the per-platform setup): https://pub.dev/packages/flutter_secure_storage
- `go_router` on pub.dev: https://pub.dev/packages/go_router
- `provider` on pub.dev: https://pub.dev/packages/provider

---

### F2. Auth Data Models

**What it requires.**
Create Dart classes to represent the JSON shapes crossing the API boundary. You need: `LoginRequest` (fields: `email`, `password`; method: `toJson()`), `RegisterRequest` (fields: `username`, `email`, `password`; method: `toJson()`), `AuthResponse` (the server's reply to both endpoints; fields: `token`, optionally `id`/`username`/`email`; method: `fromJson()`), and a `User` model for profile display. JSON field names must match exactly what Spring Boot's Jackson serializer produces (camelCase matching your Java field names). Use the same handwritten `fromJson` / `toJson` pattern already established in `ping.dart` — code generation (`json_serializable`) is not worth the tooling overhead until you have a dozen models.

**Key decision.**
Plain Dart classes vs Dart records (available since Dart 3). Records are immutable value types — good for DTOs. However, records cannot have methods, so `fromJson` would need to be a top-level function or a factory on a regular class. For small models the difference is cosmetic. Consistency with `ping.dart` (a regular class with a static `fromJson`) is reason enough to keep using that pattern here.

**Resources.**
- Flutter cookbook — JSON serialization by hand: https://docs.flutter.dev/data-and-backend/serialization/json#manual-serialization
- Dart language — records: https://dart.dev/language/records
- `json_serializable` on pub.dev (for future reference when models multiply): https://pub.dev/packages/json_serializable

---

### F3. AuthRepository

**What it requires.**
The `AuthRepository` owns all communication with the two auth endpoints and all interaction with `flutter_secure_storage`. It needs: `login(email, password)` which POSTs to `/api/v1/auth/login`, parses the `AuthResponse`, stores the token via secure storage, and returns it; `register(username, email, password)` which POSTs to `/api/v1/auth/register` and maps server error responses (409 = email taken, 400 = validation errors) into typed Dart exceptions; `logout()` which deletes the token from secure storage; and `getStoredToken()` which reads from secure storage and returns the token or null (used at app startup to determine if the user is already logged in). This class is the direct successor to `ApiModel` in `main.dart`.

**Key decision.**
Define typed exception classes (`AuthException`, `NetworkException`) rather than letting raw `http` package exceptions propagate to the ViewModel. The ViewModel should catch these typed exceptions and set a human-readable error string on the state — not a raw stack trace or HTTP status code. Your existing `main.dart` uses `on HttpException catch (e)` — extend this pattern with custom exception classes that carry a message the UI can display directly.

**Resources.**
- `http` package — making POST requests with a JSON body: https://pub.dev/packages/http
- `flutter_secure_storage` API — read, write, delete methods: https://pub.dev/documentation/flutter_secure_storage/latest/
- Flutter cookbook — fetch data from the internet (error handling patterns): https://docs.flutter.dev/cookbook/networking/fetch-data

---

### F4. AuthViewModel / AuthState

**What it requires.**
The `AuthViewModel` extends `ChangeNotifier` (matching the existing pattern in `main.dart`) and holds the current authentication state as a sealed class or enum with four states: `unauthenticated`, `loading`, `authenticated` (carrying the user's display name or ID), and `error` (carrying a human-readable message string). It exposes `login()` and `register()` methods that delegate to `AuthRepository`, update state, and call `notifyListeners()`. It also exposes `checkStoredAuth()` — called once at app startup — that reads the token from secure storage and sets state to `authenticated` if a token exists. Wrap `MaterialApp` with a `ChangeNotifierProvider<AuthViewModel>` so any descendant can access it via `context.watch<AuthViewModel>()` or `context.read<AuthViewModel>()`.

**Key decision.**
Where the `AuthViewModel` lives in the widget tree. Without `provider`, you would pass it down through constructors — workable for one screen, unwieldy for a growing app. `ChangeNotifierProvider` (from the `provider` package) solves this. This is also the moment to understand the difference between `context.watch` (rebuilds the widget when state changes) and `context.read` (reads once, no rebuild) — mixing them up is the most common source of state management bugs in Flutter.

**Resources.**
- Flutter state management — ChangeNotifier and Provider: https://docs.flutter.dev/data-and-backend/state-mgmt/simple
- `provider` package documentation: https://pub.dev/documentation/provider/latest/
- Flutter cookbook — working with ChangeNotifier: https://docs.flutter.dev/cookbook/architecture/provider

---

### F5. Login Screen

**What it requires.**
A `LoginScreen` widget with a `Form` wrapping two `TextFormField` widgets (email and password), a submit `ElevatedButton`, and a link or `TextButton` navigating to `RegisterScreen`. The password field uses `obscureText: true`. The form uses a `GlobalKey<FormState>` to call `validate()` on submit. Client-side validation here should be minimal (non-empty checks only) — the server is the source of truth. On submit, call `authViewModel.login(email, password)`. While `state == loading`, show a `CircularProgressIndicator` in place of or alongside the button. On `authenticated`, `go_router` navigates to the home screen. On `error`, display the error message inline or in a `SnackBar`.

**Key decision.**
Form management in Flutter: the `Form` + `GlobalKey<FormState>` + `TextFormField.validator` pattern is canonical but easy to wire incorrectly. The key distinction: `validator` is called on `form.validate()` (returns a string error or null); `onSaved` is called on `form.save()` (writes values somewhere). For simple forms, reading from a `TextEditingController` directly is often cleaner than using `onSaved`. Pick one approach and be consistent.

**Resources.**
- Flutter cookbook — building a form with validation: https://docs.flutter.dev/cookbook/forms/validation
- Flutter API reference — `Form` widget: https://api.flutter.dev/flutter/widgets/Form-class.html
- Flutter API reference — `TextFormField`: https://api.flutter.dev/flutter/material/TextFormField-class.html

---

### F6. Register Screen

**What it requires.**
The `RegisterScreen` is structurally identical to the login screen but adds a `username` field and optionally a `confirmPassword` field whose `validator` reads the password controller's value and returns an error if they do not match. It calls `authViewModel.register(username, email, password)`. Server-side uniqueness errors (email already taken) must surface as a visible UI message — this is where typed exceptions from `AuthRepository` pay off, since the ViewModel maps `409 Conflict` to `AuthState.error("Email already in use")`.

**Key decision.**
Define username constraints now — length range (e.g., 3–30 characters), allowed characters (alphanumeric + underscores) — and mirror them between the server (a `@Pattern` annotation on the DTO) and the client (a `RegExp` in the `validator`). Mismatched constraints between client and server create a poor experience: the client allows a username the server rejects. Decide once, enforce in both places.

**Resources.**
- Flutter cookbook — cross-field form validation: https://docs.flutter.dev/cookbook/forms/validation
- Dart `RegExp` class: https://api.dart.dev/stable/dart-core/RegExp-class.html
- Jakarta Bean Validation `@Pattern` (server-side mirror): https://jakarta.ee/specifications/bean-validation/3.0/apidocs/jakarta/validation/constraints/Pattern.html

---

### F7. Token-Aware HTTP Client (ApiClient)

**What it requires.**
After login, every protected request must carry the header `Authorization: Bearer <token>`. Rather than adding this header manually in every `http.get()` call, extract it into an `ApiClient` class that wraps `http.Client`. The `ApiClient` holds a reference to `AuthRepository`, calls `getStoredToken()` before each request, and builds the headers map. Every future feature repository (servers in Phase 2, messages in Phase 3) accepts an `ApiClient` in its constructor. This is a direct generalization of the `ApiModel` pattern already in `main.dart` — you are scaling what you already wrote, not inventing something new.

**Key decision.**
What to do when a protected request returns 401 (token expired). The cleanest strategy is to have `ApiClient` detect a 401 response, call `AuthRepository.logout()` to clear the stored token, and emit a signal (via a `Stream` or a callback) that drives `GoRouter` to navigate back to the login screen. Implementing this global 401 handler here, in one place, is far easier than adding it later to ten different repositories.

**Resources.**
- `http` package — `BaseClient` (for wrapping requests): https://pub.dev/documentation/http/latest/http/BaseClient-class.html
- Flutter architecture guide — repository pattern: https://docs.flutter.dev/app-architecture/guide
- Dart `Stream` introduction: https://dart.dev/libraries/dart-async#stream

---

### F8. Route Guarding

**What it requires.**
`go_router` supports a `redirect` callback on the `GoRouter` constructor. This callback receives the current navigation state and returns either a redirect path or null (allow). Your redirect logic: if the user is `unauthenticated` and navigating to any route except `/login` or `/register`, redirect to `/login`; if `authenticated` and navigating to `/login` or `/register`, redirect to `/home`. Pass your `AuthViewModel` as `refreshListenable` on the `GoRouter` — because `AuthViewModel` is a `ChangeNotifier`, the router re-evaluates the redirect callback every time auth state changes.

**Key decision.**
The loading / startup race condition. At app startup, `checkStoredAuth()` is async — reading from secure storage takes a moment. Until it completes, auth state is neither `authenticated` nor `unauthenticated`. If the redirect runs during this window, users with a stored token see the login screen flash before being redirected home. Handle this with a `loading` state that the redirect callback treats as "do not redirect yet". The `refreshListenable` pattern resolves this naturally: the redirect re-evaluates only after `checkStoredAuth()` calls `notifyListeners()`.

**Resources.**
- `go_router` documentation — redirection: https://pub.dev/documentation/go_router/latest/topics/Redirection-topic.html
- `go_router` — `refreshListenable`: https://pub.dev/documentation/go_router/latest/go_router/GoRouter/refreshListenable.html
- Flutter navigation overview: https://docs.flutter.dev/ui/navigation

---

## Integration Checkpoint

Before writing any Flutter auth code, verify the entire backend with a tool like `curl`, Bruno, or Postman:

1. `POST /api/v1/auth/register` with a JSON body → expect 201 with a user object (no `passwordHash`)
2. `POST /api/v1/auth/login` with the same credentials → expect 200 with a `token` field
3. Copy the token. `GET /api/v1/ping` with `Authorization: Bearer <token>` → expect 200
4. `GET /api/v1/ping` without the header → expect 401

All four passing means the backend is complete and the Flutter work can start.

---

## Critical Files to Modify

- `backend/pom.xml` — add all new dependencies (B1)
- `backend/src/main/resources/application.properties` — datasource config, JWT secret (B2)
- `backend/src/main/resources/db/migration/V1__create_users_table.sql` — new file (B3)
- `backend/src/main/java/io/github/cptfahd/backend/auth/` — new package: entity, repo, service, controller, JWT util, JWT filter, security config (B4–B11)
- `client/pubspec.yaml` — add `flutter_secure_storage`, `go_router`, `provider` (F1)
- `client/lib/` — new files: auth data models, AuthRepository, AuthViewModel, LoginScreen, RegisterScreen, ApiClient, router config (F2–F8)