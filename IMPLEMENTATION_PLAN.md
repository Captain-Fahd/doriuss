# Building a Discord Alternative — Implementation Plan

A self-hosted, extensible chat + voice + screen-share platform.
**Backend:** Java (Spring Boot) · **Client:** Flutter (one codebase → Windows first, then iOS + Android) · **Media:** LiveKit.

Solo project, budgeted at roughly **6–8 hours/week over ~30–36 weeks**. Phases are ordered so that each one produces something you can actually run and demo, and so the hardest concepts are introduced only after you have a working scaffold to hang them on.

---

## 1. The stack, and why

| Layer | Choice | Why this, for you |
|---|---|---|
| **Backend language** | Java 21 | Your requirement. Modern LTS with records, pattern matching, virtual threads. |
| **Backend framework** | Spring Boot 3.x | The best-documented, most job-relevant Java stack. Batteries included for REST, WebSockets, security, and data. |
| **Real-time transport** | WebSocket + STOMP (Spring) | Standard way to do live messaging and presence in Spring. STOMP gives you channels/topics for free. |
| **Database** | PostgreSQL | Rock-solid relational store. Fits a Discord-style domain (users, servers, channels, messages) naturally. |
| **Cache / pub-sub** | Redis | Presence, typing indicators, and later: relaying WebSocket events across multiple backend instances. |
| **Auth** | Spring Security + JWT | Stateless tokens work cleanly for a desktop/mobile client that isn't a browser. |
| **DB migrations** | Flyway | Versioned, repeatable schema changes. Non-negotiable once friends have real data. |
| **Media (voice/video/screen)** | LiveKit | Don't hand-roll WebRTC. LiveKit does voice, video, and screen share, scales past 10, and has SDKs for every platform you target. Backend only mints tokens and reacts to webhooks. |
| **Client** | Flutter (Dart) | One codebase → Windows, iOS, Android. LiveKit's Flutter SDK covers all three including screen share. Dart is easy coming from TypeScript. |
| **Client state** | Riverpod | Clean, testable state management — the modern default. |
| **Deployment** | Docker Compose on a VPS + Caddy (auto-TLS) | Reproducible, internet-facing, and cheap. Caddy handles HTTPS certificates automatically. |

**Two decisions locked from your answers:**
- *One shared client codebase* → Flutter. LiveKit's Flutter SDK is the reason this works for desktop + mobile media from a single project.
- *Managed media first* → LiveKit Cloud (has a free tier), then migrate to self-hosted LiveKit in a later phase. This keeps friends-can-join achievable early without you running TURN servers on day one.

> **Verify before you start:** pin the current versions of Spring Boot (3.x line), Flutter/Dart (stable), and the `livekit_client` package on pub.dev, plus the LiveKit Server SDK for Java on GitHub. These move a few times a year; the architecture below is version-agnostic.

---

## 2. High-level architecture

```
                        ┌──────────────────────────────┐
                        │      Flutter client          │
                        │  (Windows → iOS → Android)   │
                        │                              │
                        │  REST ──► auth, CRUD, history│
                        │  WS/STOMP ──► live messages, │
                        │              presence, typing│
                        │  LiveKit SDK ──► voice/video/│
                        │                 screen share │
                        └──────┬─────────────┬─────────┘
                               │             │
              REST + WebSocket │             │ media (WebRTC)
                               ▼             ▼
        ┌──────────────────────────────┐   ┌─────────────────────┐
        │   Spring Boot backend        │   │   LiveKit            │
        │                              │   │   (Cloud → later     │
        │  ┌────────────────────────┐  │   │    self-hosted)      │
        │  │ auth │ users │ servers │  │◄──┤  webhooks            │
        │  │ channels │ messaging   │  │──►│  (mint room tokens)  │
        │  │ presence │ rooms       │  │   │                      │
        │  └────────────────────────┘  │   └─────────────────────┘
        │        │            │        │
        └────────┼────────────┼────────┘
                 ▼            ▼
          ┌────────────┐  ┌────────┐
          │ PostgreSQL │  │ Redis  │
          └────────────┘  └────────┘
```

The key architectural idea: **the backend never touches the audio/video bytes.** Media flows client ↔ LiveKit directly. Your Spring app only decides *who is allowed into which room* (by minting scoped LiveKit access tokens) and *reacts to room events* (via LiveKit webhooks). This is what keeps a 10-person call from ever bottlenecking on your server, and it's the same pattern that scales to hundreds later.

---

## 3. Domain model (the shape of the data)

Start here; almost everything else derives from it.

- **User** — id, username, display name, email, password hash, avatar, status.
- **Server** (Discord calls these "guilds") — id, name, owner, icon.
- **Membership** — join table: which user belongs to which server, with a role.
- **Channel** — id, server_id, name, type (`TEXT` | `VOICE`), position.
- **Message** — id, channel_id, author_id, content, created_at, edited_at, (later: attachments, reply_to).
- **Attachment** — id, message_id, url, mime, size *(later phase)*.
- **Role / Permission** — *(later phase; design the Membership table now so roles slot in cleanly)*.
- **VoiceSession** — transient: who is currently in which voice channel (backed by Redis + LiveKit webhooks, not a heavy DB table).

Design tip: give every table a `created_at`/`updated_at` and use UUIDs for public-facing ids. You'll thank yourself when you add mobile sync and audit features.

---

## 4. Principles that keep it extensible

You asked for architecture that welcomes new features. Four rules do most of the work:

1. **Package by feature, not by layer.** `com.yourapp.messaging`, `com.yourapp.voice`, `com.yourapp.servers` — each owning its controllers, services, and repositories. Adding "reactions" later means adding a package, not surgery across five folders.
2. **Version the API from message one** (`/api/v1/...`). Mobile clients ship on their own schedule and can't all update at once; versioning stops one change from breaking older installs.
3. **Emit internal events for side effects.** When a message is saved, publish an event; presence, notifications, and search indexing subscribe to it. New reactive features hook in without editing the write path. Start with Spring's `ApplicationEventPublisher`; graduate to a real broker only if you need it.
4. **Repository pattern on the client.** Flutter UI talks to `MessageRepository`, not to `dio` or the WebSocket directly. Swapping transport, adding caching, or writing tests then never touches the widgets.

---

## 5. The phased roadmap

Each phase lists **the goal**, **what you build**, **what you learn**, and **resources**. Ship each phase to a runnable state before moving on.

### Phase 0 — Foundations & the full-loop hello world · ~1–2 weeks
**Goal:** a Flutter Windows app that calls one Spring Boot endpoint and shows the result. Prove the whole pipe end-to-end before adding complexity.
**Build:** empty Spring Boot project with one `/api/v1/ping` endpoint; empty Flutter desktop app that fetches and displays it; Git repo with a sensible structure (backend + client as separate folders or a monorepo); Docker installed and a Postgres container running locally.
**Learn:** Spring Boot project anatomy; Dart syntax and the Flutter widget tree; how a desktop Flutter app talks HTTP.
**Resources:**
- Spring: the official *Building a RESTful Web Service* guide at spring.io/guides, plus the Spring Boot reference docs.
- Dart: the language tour at dart.dev/language.
- Flutter: flutter.dev — "Get started" + the desktop support page; the *Write your first Flutter app* codelab.

### Phase 1 — Auth & accounts · ~2–3 weeks
**Goal:** register, log in, stay logged in.
**Build:** User entity + Flyway migration; register/login endpoints; BCrypt password hashing; JWT issuance + a Spring Security filter that validates tokens; Flutter login/register screens; secure token storage on the client (`flutter_secure_storage`).
**Learn:** Spring Security filter chain; JWT structure and pitfalls; why you hash (never encrypt) passwords; PostgreSQL basics + migrations with Flyway.
**Resources:**
- Baeldung's Spring Security series and their "Spring Security with JWT" articles.
- Spring Security reference docs (architecture chapter).
- Riverpod docs (riverpod.dev) — introduce it here for auth state.

### Phase 2 — Servers, channels & membership · ~2–3 weeks
**Goal:** create a server, add channels, join servers.
**Build:** Server/Channel/Membership entities + migrations; REST CRUD for each; the Discord-style client shell (server rail on the left, channel list, main pane); navigation with a router (`go_router`).
**Learn:** relational modelling and JPA/Hibernate relationships; REST resource design; Flutter layout, navigation, and responsive structure.
**Resources:**
- Baeldung JPA/Hibernate relationship guides.
- Vandad Nahavandipoor's free Flutter course (YouTube) or the Academind *Flutter & Dart – The Complete Guide* for layout/navigation depth.

### Phase 3 — Real-time text messaging · ~4–5 weeks *(the meaty one)*
**Goal:** live chat that feels instant, with history and presence.
**Build:** WebSocket + STOMP endpoint in Spring; subscribe to per-channel topics; broadcast new messages to subscribers; persist + paginate message history; presence (online/offline) and typing indicators via Redis pub-sub; Flutter WebSocket client (`stomp_dart_client`) wired through a `MessageRepository`.
**Learn:** the WebSocket lifecycle and STOMP topics; optimistic UI updates and reconnection handling; why presence lives in Redis, not Postgres; keeping a scroll-back list performant.
**Resources:**
- Spring's *Using WebSocket to build an interactive web application* guide.
- LiveKit is not involved yet — this is pure Spring + Flutter real-time work.
- Redis docs on pub/sub.

### Phase 4 — Voice chat with LiveKit · ~4–5 weeks
**Goal:** join a voice channel and talk to up to 10 people.
**Build:** LiveKit Cloud project + API keys; backend endpoint that mints a scoped LiveKit **access token** (room = voice channel id, identity = user); LiveKit webhook receiver so the backend knows who joined/left (updates presence); Flutter integration with `livekit_client` — join/leave, mic mute, per-participant volume, a simple in-call UI.
**Learn:** the token-minting security model (your server is the gatekeeper); the mental model of "rooms" and "tracks"; enough WebRTC vocabulary (SFU, ICE, tracks) to reason about what LiveKit does for you.
**Resources:**
- docs.livekit.io — start with "Realtime SDK" concepts, the Server SDK (Java) for token minting, and the Flutter quickstart.
- LiveKit's example apps repo on GitHub (there's a Flutter sample).
- Background reading: the WebRTC chapter of Ilya Grigorik's *High Performance Browser Networking* (free at hpbn.co) — concepts only, no need to implement any of it.

### Phase 5 — Screen sharing & video · ~2–3 weeks
**Goal:** share a screen; optionally turn on camera.
**Build:** publish a screen-share track via the LiveKit Flutter SDK; handle the OS screen-capture permission on Windows; UI to view a participant's shared screen and toggle your own camera; grid/spotlight layout for the call view.
**Learn:** track types (audio vs camera vs screen); platform permissions; managing multiple simultaneous media tracks in the UI.
**Resources:**
- LiveKit docs: "Screen sharing" and "Camera & microphone" pages, plus the Flutter SDK API reference.

### Phase 6 — Harden & deploy internet-facing · ~3–4 weeks
**Goal:** friends can join from their own machines over the internet.
**Build:** Dockerfile for the backend; `docker-compose.yml` bundling backend + Postgres + Redis; a VPS (Hetzner or DigitalOcean are cheap and simple); a domain name; Caddy as reverse proxy for automatic HTTPS; externalised config + secrets (env vars, not hard-coded keys); CORS, rate limiting, and a basic security pass; structured logging.
**Learn:** containerisation; reverse proxies and TLS; the difference between "works on my machine" and "works for someone across the country"; secrets hygiene.
**Resources:**
- Docker's official getting-started docs.
- Caddy docs (the automatic-HTTPS quickstart).
- Your VPS provider's "deploy Docker Compose" tutorial.

### Phase 7 — Self-host the media server *(optional, later)* · ~2–3 weeks
**Goal:** own your whole stack; drop the managed dependency.
**Build:** run LiveKit's open-source server via Docker; stand up a TURN server for clients behind strict NATs; point the backend and clients at your own LiveKit instead of Cloud.
**Learn:** why NAT traversal and TURN exist; the real cost/effort trade-off of self-hosting media (this is the lesson — you'll appreciate what Cloud did for you).
**Resources:**
- LiveKit's self-hosting / deployment docs and their Docker examples.

### Phase 8 — Mobile expansion · ~3–4 weeks
**Goal:** the same app on iOS and Android from the same codebase.
**Build:** iOS + Android build configs; platform permission prompts (mic, camera, screen capture); test voice/screen-share on real devices; handle app-lifecycle edge cases (backgrounding a call).
**Learn:** Flutter's platform channels and permission handling; iOS signing/provisioning realities; mobile media quirks.
**Resources:**
- Flutter docs: "Build and release for iOS/Android"; `permission_handler` package.
- LiveKit's mobile-specific notes (background audio, CallKit on iOS).

### Phase 9+ — Extensibility playground
Now the architecture earns its keep. Each of these is a self-contained feature package: **roles & permissions**, **message reactions**, **threads/replies**, **file & image uploads** (object storage like S3/R2), **push notifications**, **search**, **read receipts**. Pick whichever teaches you the most.

---

## 6. Rough timeline

| Phase | Weeks | Cumulative |
|---|---|---|
| 0 · Foundations | 1–2 | ~2 |
| 1 · Auth | 2–3 | ~5 |
| 2 · Servers/channels | 2–3 | ~8 |
| 3 · Real-time messaging | 4–5 | ~13 |
| 4 · Voice (LiveKit) | 4–5 | ~18 |
| 5 · Screen share/video | 2–3 | ~21 |
| 6 · Deploy internet-facing | 3–4 | ~25 |
| 7 · Self-host media *(optional)* | 2–3 | ~28 |
| 8 · Mobile | 3–4 | ~32 |

At ~6–8 hrs/week that lands around **30–36 weeks** to a mobile-capable, internet-facing app, with the optional self-hosting phase adding a couple more. Slip is normal — Phase 3 and Phase 4 are where most people spend longer than expected, so protect that time.

---

## 7. Your very first concrete step

Before writing any feature code:

1. Install the JDK (21), a Java IDE (IntelliJ IDEA Community is ideal for Spring), the Flutter SDK, and Docker Desktop.
2. Generate an empty Spring Boot project at **start.spring.io** with dependencies: *Web, Security, WebSocket, Data JPA, PostgreSQL Driver, Flyway, Validation*.
3. `flutter create` a new app and confirm it runs as a **Windows** desktop target.
4. Do Phase 0: one `/api/v1/ping` endpoint, one Flutter screen that calls it. Once that round-trip works, you have your skeleton and everything else is incremental.

That single working loop is the highest-leverage thing you can build this week — it de-risks the entire toolchain before you've committed to any real feature.
