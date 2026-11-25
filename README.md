# The Bank of Banco Imobiliário

[![Backend CI](https://github.com/FelipeM525/monopoly-bank/actions/workflows/backend.yml/badge.svg)](https://github.com/FelipeM525/monopoly-bank/actions/workflows/backend.yml)
[![Frontend CI](https://github.com/FelipeM525/monopoly-bank/actions/workflows/frontend.yml/badge.svg)](https://github.com/FelipeM525/monopoly-bank/actions/workflows/frontend.yml)

A web app that replaces paper and a calculator for the banker in the Brazilian board game **Banco Imobiliário**. It tracks balances and moves money between players and the bank. The board, dice, cards and turns are out of scope.

**Live demo:** the URL is shared separately with the reviewers (the repository is public). The API docs are at `/api/swagger-ui.html` on the same address.

## Features

| Id | Requirement | Status |
|----|-------------|--------|
| R1 | Create a game with 2–6 players (unique name, unique pawn color, editable starting balance with a default) | Done |
| R2 | One dashboard with every player's balance and the cash held by the bank | Done |
| R3 | Move money player → bank, bank → player, player → player | Done |
| R4 | Transaction history (payer, receiver, amount, reason, time), filterable by player | Done |
| R5 | Atomic transfers, no overdraft, total money conserved, clear error messages | Done |
| R6 | Game state survives closing the app or restarting the server | Done |

Extras implemented: several games at the same time, game rules in configuration, quick amount buttons (+10/+50/+100/+200), reason tags, live post-transaction balance preview, Swagger/OpenAPI docs, a balance audit endpoint.

## Tech stack

- **Backend** (`/backend`): Java 21, Spring Boot, Spring Data JPA, Bean Validation, Flyway, PostgreSQL 16, Lombok, Testcontainers. No Spring Security (no auth required).
- **Frontend** (`/frontend`): React, TypeScript, Vite, Tailwind CSS, shadcn/ui, TanStack Query, React Router, Vitest.
- **Infrastructure**: Docker Compose (`db`, `backend`, `frontend`), GitHub Actions CI.

I chose a separate backend and frontend because the money rules need transactions, row locking and a real relational database, and a typed API keeps the UI simple and fast.

## Run it

### Everything in Docker (from a clean clone)

```bash
docker compose up --build
```

- App: http://localhost:3000
- API: http://localhost:8080
- Swagger UI: http://localhost:8080/api/swagger-ui.html

The app is served by Nginx, which also proxies `/api` to the backend, so no CORS setup is needed.

### Local development

Run only the database in Docker and the apps on your machine for hot reload:

```bash
docker compose up -d db
cd backend && ./mvnw spring-boot:run      # http://localhost:8080
cd frontend && npm install && npm run dev  # http://localhost:5173 (proxies /api to :8080)
```

Settings can be overridden with environment variables; see `.env.example`.

## Run the tests

```bash
cd backend && ./mvnw verify       # needs Docker running (Testcontainers starts PostgreSQL)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
```

The backend tests run against a real PostgreSQL container, not an in-memory database, because locking and constraints are part of what is being verified.

## Money rules and decisions

- **Integer money only.** Every amount is stored as `BIGINT` / `long` in **centavos**. Floating point is never used. The frontend converts to and from `R$` using string and integer math only (`19,99` → `1999`).
- **The bank is an account.** The bank is a row in `accounts` (`type = 'BANK'`) next to the players (`type = 'PLAYER'`), so every transfer is the same `from_account → to_account` ledger entry.
- **Atomic transfers.** A transfer debits, credits and writes the ledger row in one database transaction. If anything fails, nothing changes.
- **No deadlocks, no double spending.** Both accounts are locked with `SELECT ... FOR UPDATE` in a single query ordered by `id ASC`. Every transfer locks the lower id first, so two opposite transfers (A→B and B→A) cannot wait on each other. A second payment on the same account waits for the first to commit and then sees the new balance.
- **Double-tap protection.** Each transfer requires a client-generated `Idempotency-Key` (UUID) with a `UNIQUE` constraint. The frontend creates the key once per form submission and reuses it on retries. A repeated key returns the existing transaction (HTTP 200) and does not charge again. The same key with different data is rejected (422). Concurrent duplicates are handled too.
- **Auditable balances.** Each account stores `initial_balance_cents`. `GET /api/games/{id}/audit` rebuilds every balance from the initial balance plus the ledger and compares it with the stored balance.
- **Several games at once.** Every row is scoped by `game_id`, and a transfer is rejected if an account belongs to another game.

### Decision: does the bank have limited cash?

**Yes, by default.** The bank starts with a configured amount (default R$ 20.000,00) and cannot pay more than it holds. This is controlled by `game.rules.bank.unlimited` (default `false`). Setting it to `true` lets the bank pay without a limit, and the dashboard then shows `∞ Unlimited`.

### Decision: what happens when a player cannot pay?

The transfer is **rejected with HTTP 422** and a readable message (for example, "Ana has R$ 120,00 and cannot pay R$ 200,00."). Nothing is changed and nothing is recorded. The live balance preview in the form also turns red before the banker confirms. Selling or mortgaging properties is outside the bank's scope, so the table settles that outside the app.

### Configuration

Game rules live in `backend/src/main/resources/application.yaml` under `game.rules` and can be overridden by environment variables, with no code changes:

| Setting | Env var | Default |
|---------|---------|---------|
| `starting-balance-cents` | `GAME_STARTING_BALANCE_CENTS` | 150000 (R$ 1.500,00) |
| `salary-cents` | `GAME_SALARY_CENTS` | 20000 (R$ 200,00) |
| `min-players` / `max-players` | `GAME_MIN_PLAYERS` / `GAME_MAX_PLAYERS` | 2 / 6 |
| `bank.initial-cash-cents` | `GAME_BANK_INITIAL_CASH_CENTS` | 2000000 (R$ 20.000,00) |
| `bank.unlimited` | `GAME_BANK_UNLIMITED` | false |
| `reasons` | — | Purchase, Rent, Tax, Salary, Other |

Adding a transaction reason is a one-line change to the `reasons` list.

## API

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/config` | Defaults, pawn colors, reasons, bank policy |
| POST | `/api/games` | Create a game with 2–6 players |
| GET | `/api/games` | List games (resume / several games) |
| GET | `/api/games/{id}` | Dashboard: balances, bank cash, total money |
| GET | `/api/games/{id}/audit` | Rebuild balances from the ledger and compare |
| POST | `/api/games/{id}/transactions` | Transfer money (requires `Idempotency-Key` header) |
| GET | `/api/games/{id}/transactions?accountId=&page=&size=` | History, newest first, optional player filter |

Errors use RFC 7807 `ProblemDetail`. Validation errors include an `errors: {field: message}` map. Full interactive docs are in the Swagger UI.

## Deployment

The live demo runs on AWS: one EC2 instance (Ubuntu 24.04) runs the `backend` and `frontend` containers from `docker-compose.prod.yaml`, and the database is a private Amazon RDS PostgreSQL 16 instance. The RDS security group only accepts port 5432 from the EC2 security group, and the database has no public address. Production settings come from a `.env` file on the server (see `.env.prod.example`).

## What I would do next

- **Undo the last transaction** as a reversal entry (`reversal_of_transaction_id`), never deleting history. The schema and history API already support it; the endpoint and button are not built yet.
- **Export the history** as CSV/JSON.
- **Dark mode, larger touch targets and keyboard shortcuts.** The theme variables are in place.
- **HTTPS and a domain** for the live demo (today it is plain HTTP on a raw IP address), plus an Elastic IP so the address does not change when the instance restarts.
- A **bankruptcy flow** that moves a broke player's remaining balance to the creditor.
- **End-to-end browser tests** for the main table flow.

## AI notes

I used AI assistants (Claude and Gemini models through the Antigravity coding agent) to plan the issues, scaffold code and tests, and explain concepts. They helped most with the repetitive parts: DTOs, tests, Dockerfiles, CI and cloud setup. They got in the way when they assumed things that were not in the specification, which I corrected, and when generated code used library versions I had to check against what was actually installed. I reviewed and ran the code before committing, and I can explain each part, especially the locking and idempotency logic.
