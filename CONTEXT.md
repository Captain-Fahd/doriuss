# Doriuss — Project Context

Orientation for anyone (human or AI) picking up this repo: what it is, where it stands, and what doesn't match the plan.

*Snapshot: 2026-10-31, at commit `b2b7923` (2026-07-12). Update "Current state" and "Known issues" as work lands.*

**Where to look for what**

| Document | Use it for |
|---|---|
| `README.md` | Public summary of the project |
| `IMPLEMENTATION_PLAN.md` | Full roadmap, domain model, architecture principles |
| `PHASE_1_TASKS.md` | Task-by-task breakdown of the current phase (B1–B11, F1–F8) |
| This file | Current state, decisions that override the above, known issues |

---

## What it is

A self-hosted, extensible Discord-style platform: text chat, voice channels (up to ~10 people), screen sharing, and optional camera, organised into servers and channels.

It is a solo project, budgeted at 6–8 hours/week over ~30–36 weeks, and doubles as a learning exercise. The plan docs deliberately describe tasks and resources without giving code, so the owner can write the implementation themselves. Explain and guide unless asked to implement.

## Architecture

```
Flutter client
  ├── REST      → auth, CRUD, message history
  ├── WebSocket → live messages, presence, typing (STOMP)
  └── LiveKit   → voice / video / screen share

Spring Boot backend → PostgreSQL, Redis
LiveKit             → media (client ↔ LiveKit directly)
```

**The backend never touches audio or video bytes.** It mints scoped LiveKit access tokens and reacts to LiveKit webhooks.

Planned principles (`IMPLEMENTATION_PLAN.md` §4):

1. Package by feature, not by layer.
2. Version the API from the start (`/api/v1/...`).
3. Emit internal events for side effects (`ApplicationEventPublisher` first).
4. Repository pattern on the client: widgets never call HTTP or WebSocket directly.

## Stack: actual vs planned

| Layer | In the repo now | Planned, not yet present |
|---|---|---|
| Backend | Java 21, Spring Boot **4.1.0**, Maven wrapper | |
| Data | PostgreSQL 17 (Compose), Spring Data JPA, Flyway dependencies | Redis |
| Auth | Spring Security starter, JJWT 0.13.0 (dependencies only) | BCrypt config, JWT service and filter |
| Other backend | Validation starter, Lombok, devtools | WebSocket + STOMP, LiveKit server SDK |
| Client | Flutter, Dart `^3.12.2`, `http` | `flutter_secure_storage`, `go_router`, `provider`, Riverpod, `stomp_dart_client`, `livekit_client` |
| Deploy | `backend/Dockerfile`, `docker-compose.yml` (app + db) | Redis service, Caddy, VPS |

**Decisions that override the older docs**

- **Spring Boot is 4.1.0**, not the 3.x stated in the README and plan. That means Spring Security 7 and Hibernate 7, so tutorials written for older versions may not apply. `WebSecurityConfigurerAdapter` is gone.
- **Client state is `ChangeNotifier` + `provider` for Phase 1**, with Riverpod introduced in Phase 2. The plan's Phase 1 says Riverpod; `PHASE_1_TASKS.md` supersedes it.
- **Refresh tokens are deferred to Phase 6.** Phase 1 uses a single 7-day HS256 access token.

## Repository layout

```
backend/                              Spring Boot app (io.github.cptfahd.backend)
  src/main/java/.../
    BackendApplication.java
    web/PingController.java           GET /api/v1/ping
    model/User.java                   JPA entity (Phase 1)
    model/Message.java                JPA entity (early Phase 3 stub)
  src/main/resources/application.properties
  Dockerfile
client/                               Flutter app, Windows target first
  lib/main.dart                       App entry and the whole ping screen (MVVM in one file)
  lib/ping.dart                       Ping model, the hand-written fromJson pattern to follow
docker-compose.yml                    app (:8000) + db (:5432)
```

## Current state

**Phase 0 (foundations) is done; Phase 1 (auth) is in progress.** The last commit is labelled "Phases B1–B4", but the tree shows B3 is still missing:

| Task | Status | Notes |
|---|---|---|
| B1 Maven dependencies | Done | The backend compiles (`mvnw compile`, checked at this snapshot) |
| B2 Postgres + Compose | Mostly done | See known issues 3 and 4 |
| B3 Flyway `users` migration | **Not done** | No `db/migration` directory exists |
| B4 User entity | Done | Lives in `model/`; the task doc expects `auth/` |
| B5–B11 repository, BCrypt, register, JWT, login, filter chain | Not started | |
| F1–F8 Flutter auth | Not started | Start after the backend passes the integration checkpoint |

**Implementation order:** B1 → B2 → B3 → B4 + B5 → B6 → B7 + B10 → B8 → B9 → B11, then F1 → F2 → F3 + F7 → F4 → F5 + F6 → F8.

**Next step:** write `V1__create_users_table.sql` (B3) and resolve known issue 1, then continue with B5.

## Known issues

Found by reading the code. Only the compile was run; the runtime effects below are expected behaviour and haven't been observed.

**Blocking**

1. **The app should fail to start.** `ddl-auto=validate` requires tables to exist, but there are no migrations to create `users` or `messages`. B3 fixes `users`. `Message` also needs a migration, or should be removed until Phase 3.
2. **`Message.id` combines `UUID` with `GenerationType.IDENTITY`.** Postgres identity columns are integer-only, so this mapping should fail. `User` uses `GenerationType.UUID`, which is the pattern to copy.

**Configuration**

3. **Database name mismatch in Compose.** The container creates `doriuss_postgres`, but the app connects to `jdbc:postgresql://db:5432/postgres`. Connections should still succeed, because the default `postgres` database exists, so the data would silently land in the wrong database.
4. **Credentials are hard-coded.** `secret` appears in `docker-compose.yml` and as the fallback in `application.properties`, which B2 says to avoid. There is no `.env` file or root `.gitignore` entry for one yet. The fallback username (`postgres`) also doesn't match the container's user (`doriuss_postgres`), so running the backend outside Compose needs `SPRING_DATASOURCE_*` set.

**Behaviour changes to expect**

5. **`/api/v1/ping` now needs authentication.** Adding the Security starter locks every route by default, and there is no `SecurityFilterChain` yet. The Flutter ping screen should show an error until B10 is in place.
6. **`BackendApplicationTests` needs a working database and schema**, since `@SpringBootTest` loads the full context. It can't pass until issues 1 and 2 are fixed.

**Drift and cleanup**

7. **Packages are by layer** (`model/`, `web/`), against principle 1. `PHASE_1_TASKS.md` expects a feature package, `auth/`, so `User` should move there during B5–B11.
8. **`Message` doesn't match the domain model.** It uses `text` rather than `content`, has no `channel_id` or `author_id`, and uses `LocalDateTime` where `User` uses `OffsetDateTime`. It also has unused imports. The plan wants timezone-aware timestamps.
9. **Client error handling is narrow.** `ApiViewModel.pingApi` only catches `HttpException`, so a connection failure goes uncaught. It also pings twice on startup, from both the constructor and `initState`. The API host is hard-coded to `localhost:8000`.
10. **Minor items:**
    - The Dockerfile's stage comments are swapped ("Run stage" labels the build stage).
    - Some starters in `pom.xml` pin `4.1.0` when the parent already manages that version.
    - Seven `.idea/` files are tracked, which produces noise like the current `Dart_Packages.xml` change.

## Conventions

**Backend**
- API routes live under `/api/v1/`.
- Entities are regular classes with Lombok; response shapes and DTOs are records (as in `PingResponse`).
- Primary keys are UUIDs; tables have `created_at` and `updated_at` as `TIMESTAMPTZ`.
- The password field is always `passwordHash`, and it never appears in a response.
- **Flyway owns the schema** (`V{n}__{description}.sql` in `src/main/resources/db/migration/`). Hibernate only validates it.
- Errors use one JSON shape from a `@RestControllerAdvice`, e.g. `{ "errors": { "email": "must not be blank" } }`.
- Login failures return 401 for both unknown email and wrong password, to prevent account enumeration.
- Secrets come from environment variables.

**Client**
- MVVM: Model → `ChangeNotifier` ViewModel → View via `ListenableBuilder`.
- Hand-written `fromJson`/`toJson` on regular classes, following `ping.dart`; no code generation yet.
- Feature repositories take an `ApiClient` and throw typed exceptions, and ViewModels turn those into readable messages (from F3 and F7 onward).

## Running it

Run from the repo root unless noted. Given known issues 1 and 2, the backend won't start until they're fixed.

```sh
docker compose up --build        # backend + Postgres

docker compose up db             # Postgres only
cd backend && ./mvnw spring-boot:run   # needs SPRING_DATASOURCE_* set (issue 4)

cd backend && ./mvnw test        # needs the database running
cd client && flutter run -d windows
```

The backend listens on `:8000`, and Postgres on `:5432`.

**Phase 1 backend checkpoint** (run before any Flutter auth work):

1. `POST /api/v1/auth/register` → 201, with no `passwordHash` in the response
2. `POST /api/v1/auth/login` → 200, with a `token`
3. `GET /api/v1/ping` with `Authorization: Bearer <token>` → 200
4. `GET /api/v1/ping` without the header → 401

## Roadmap

| Phase | Focus | Status |
|---|---|---|
| 0 | Foundations: ping endpoint + Flutter screen | Done |
| 1 | Auth & accounts | **In progress** |
| 2 | Servers, channels, membership; Riverpod | |
| 3 | Real-time text (WebSocket/STOMP, Redis presence) | |
| 4 | Voice (LiveKit Cloud) | |
| 5 | Screen share & video | |
| 6 | Harden & deploy (VPS, Caddy, secrets, refresh tokens) | |
| 7 | Self-host LiveKit + TURN *(optional)* | |
| 8 | Mobile (iOS + Android) | |
| 9+ | Roles, reactions, threads, uploads, push, search | |
