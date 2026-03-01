# Coding Assistant Service

Quarkus service that accepts helpdesk BUG tickets, runs an (asynchronous) AI Coding Assistant via OpenAI's `codex` CLI + git automation. First the likely cause of the bug is identified with a % certainty that this is cause. If the certainty is over 60% (configurable), the assistant will propose a fix in a PR, and sends this PR back as callback to Helpdesk client app.

[Quick start](#quick-start-run--port--curl) | [Contract](#contract) | [Job flow](#job-flow) | [Parallel execution](#parallel-execution) | [Prerequisites](#prerequisites) | [Configuration](#configuration) | [First-time setup notes](#first-time-setup-notes)

## Quick start (run + port + curl)

- Port: `8085` (configured in `src/main/resources/application.properties`)

Run in dev mode:

```bash
mvn quarkus:dev
```

Run packaged app:

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

Submit a job:

Logs are written to `./job-logs/` (24h retention, configurable).  
Live logs can be inspected at `localhost:8085` (open before launching the job).

```bash
curl -X POST "http://localhost:8085/api/coding-assistant/jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 123,
    "originalRequest": "When user goes to Book Appointment, fills out all fields and clicks Confirm Appointment, it takes them to an error page instead of booking. It happens every time.",
    "repoUrl": "https://github.com/LizeRaes/mediflow-user-facing",
    "confidenceThreshold": 0.60
  }'
```

## Contract

### 1) Helpdesk -> Coding Assistant

- `POST /api/coding-assistant/jobs`
- Fast async response with `ACCEPTED` or `REJECTED`

Request example:

```json
{
  "ticketId": 123,
  "originalRequest": "User sees no dark mode toggle",
  "repoUrl": "https://github.com/LizeRaes/mediflow-user-facing",
  "confidenceThreshold": 0.60
}
```

### 2) Coding Assistant -> Helpdesk Callback

- `POST` to configured callback URL (`quarkus.rest-client.helpdesk-callback.url`)
- Header: `Authorization: Bearer <app.callback.auth-token>`
- Helpdesk validates this token against `app.coding-assistant.callback.auth-token`

Callback payload:

```json
{
  "ticketId": 123,
  "prUrl": "https://github.com/org/repo/pull/123"
}
```

## Job flow

1. Clones/updates target repo.
2. Phase A (analyze): run the AI Coding Assistant with strict JSON output for likelyCause (single text field) + confidence.
3. If confidence <= threshold: stop and log (no callback).
4. Phase B (fix): create `bugfix/ticket-<id>` branch, run the AI Coding Assistant with "do not run git commands".
5. If fix produces no repository changes: stop and log (no callback).
6. Phase C (git/PR): Java commits, pushes, runs `gh pr create`, callback `PR_CREATED`.
7. Any error: stop and log (no callback).

## Parallel execution

- Parallel jobs are an explicit feature of this demo.
- Max concurrent workers is configured via `app.jobs.max-concurrency` (default `3`).
- Jobs targeting the same repository use workspace slots under `repos/<repo>/slot-N`.
- Only one running job uses a given slot at a time; parallel jobs get different slots.
- This gives one local clone per *parallel running job* (reused later), not one clone per submitted job.
- Before each job starts in a slot, the repo is force-synced to `origin/<base-branch>`.
- Branch names use the lowest free suffix (`bugfix/ticket-<id>`, then `-2`, `-3`, ...) to avoid branch-name collisions.

## Prerequisites

- Java 25+
- Maven
- OpenAI `codex` CLI installed and authenticated
- `git`
- `gh` CLI installed and authenticated

## Configuration

In `src/main/resources/application.properties`:

- `quarkus.http.port=8085`
- `app.repos-root=./repos`
- `app.logs-root=./job-logs`
- `app.logs.retention-hours=24`
- `app.base-branch=main`
- `app.jobs.max-concurrency=3`
- `app.ai-coding-assistant.model=` (optional)
- `app.callback.auth-token=...`
- `quarkus.rest-client.helpdesk-callback.url=http://localhost:8080/api/coding-assistant`

For local demo defaults, keep `app.callback.auth-token` (coding-assistant) equal to
`app.coding-assistant.callback.auth-token` (helpdesk).

Request fields are mandatory in each job submission: `ticketId`, `originalRequest`, `repoUrl`, and `confidenceThreshold`.

## First-time setup notes

If this is your first time running the service, use this checklist:

1. Install AI Coding Assistant CLI (`codex`):

```bash
npm i -g @openai/codex
```

2. Run `codex` once in your terminal and complete setup/auth.  
   If `OPENAI_API_KEY` is already available in your environment, the AI Coding Assistant will detect it automatically.
3. For GitHub operations (`gh`), the first PR flow may prompt for GitHub access/authorization.
4. The first run may take longer because the target repository must be cloned before processing starts.

