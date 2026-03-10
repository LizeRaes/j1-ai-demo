# Ports And Endpoints

Canonical runtime map for local startup via `./start-all.sh`.

## App services

| Component | Port | Base URL | Notes |
|---|---:|---|---|
| `medicapt-user-facing` | 8083 | `http://localhost:8083` | Patient-facing web app |
| `helpdesk` | 8080 | `http://localhost:8080` | System-of-record ticketing |
| `ai-triage` | 8081 | `http://localhost:8081` | Classification + enrichment orchestrator |
| `urgency` | 8086 | `http://localhost:8086` | Urgency scoring |
| `similar-tickets` | 8082 | `http://localhost:8082` | Ticket similarity service |
| `company-rag` | 8084 | `http://localhost:8084` | Company document retrieval/RAG |
| `coding-assistant` | 8085 | `http://localhost:8085` | Async coding assistant callbacks |

## Databases and infra

| Component | Port | Endpoint | Notes |
|---|---:|---|---|
| `mysql` | 3306 | `localhost:3306` | Helpdesk database |
| `ticket oracle` | 1521 | `localhost:1521/freepdb1` | Similar-tickets Oracle instance |
| `company oracle` | 1522 | `localhost:1522/freepdb1` | Company-rag Oracle instance |
| `docling` | 5001 | `http://localhost:5001/openapi.json` | Document preprocessing service |

## Optional MCP variants (not started by default)

| Component | Port/Transport | Endpoint | Notes |
|---|---|---|---|
| `urgency-mcp-helidon` | 9090 | `http://localhost:9090/urgency` | MCP over HTTP/SSE for ai-triage MCP mode |

## Source of truth

- Startup order and health checks: `start-all.sh`
- AI-triage downstream URLs: `services/ai-triage/src/main/resources/application.properties`
- Container port mappings: `docker-compose.yml` and `services/company-rag/docker-compose.yml`
