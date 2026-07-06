# Doriuss

A self-hosted, extensible chat platform with voice, video, and screen sharing — a Discord-style alternative you own end to end.

**Backend:** Java 21 + Spring Boot · **Client:** Flutter (Windows first, then iOS + Android) · **Media:** LiveKit

---

## What it does

- Text chat with real-time messaging, presence, and typing indicators
- Voice channels for multi-party calls (up to ~10 people)
- Screen sharing and optional camera
- Servers, channels, and membership — the familiar server/channel model

Media never flows through the backend. Spring Boot handles auth, CRUD, and WebSocket messaging; LiveKit handles voice, video, and screen share. The server only mints scoped access tokens and reacts to room webhooks.

---

## Stack

| Layer | Choice |
|---|---|
| API & real-time | Spring Boot 3.x, REST, WebSocket + STOMP |
| Database | PostgreSQL + Flyway migrations |
| Cache / pub-sub | Redis (presence, typing, multi-instance relay) |
| Auth | Spring Security + JWT |
| Media | LiveKit (Cloud first, self-hosted later) |
| Client | Flutter + Riverpod |
| Deploy | Docker Compose, Caddy (auto-TLS) on a VPS |

---

## Architecture

```
Flutter client
  ├── REST      → auth, CRUD, message history
  ├── WebSocket → live messages, presence, typing
  └── LiveKit   → voice / video / screen share

Spring Boot backend → PostgreSQL, Redis
LiveKit             → media (client ↔ LiveKit directly)
```

---

## Roadmap

Phased build over ~30–36 weeks at 6–8 hrs/week. Each phase ships something runnable.

| Phase | Focus |
|---|---|
| 0 | Foundations — ping endpoint + Flutter hello world |
| 1 | Auth & accounts |
| 2 | Servers, channels & membership |
| 3 | Real-time text messaging |
| 4 | Voice chat (LiveKit) |
| 5 | Screen sharing & video |
| 6 | Deploy internet-facing |
| 7 | Self-host media *(optional)* |
| 8 | Mobile (iOS + Android) |
| 9+ | Reactions, roles, uploads, search, … |

See [IMPLEMENTATION_PLAN.md](./IMPLEMENTATION_PLAN.md) for the full plan — domain model, architecture principles, per-phase build details, and resources.

---

## Getting started

1. Install JDK 21, IntelliJ IDEA (or your Java IDE), Flutter SDK, and Docker.
2. Scaffold the backend at [start.spring.io](https://start.spring.io) with: Web, Security, WebSocket, Data JPA, PostgreSQL Driver, Flyway, Validation.
3. `flutter create` a desktop app and confirm it runs on **Windows**.
4. Complete Phase 0: `/api/v1/ping` on the backend, one Flutter screen that calls it.

That round-trip proves the toolchain before any feature work.
