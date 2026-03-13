# Urgency Service

Direct HTTP urgency scoring service used by `ai-triage`.

## Current behavior

- Endpoint: `POST /api/urgency/v1/score`
- Port: `8086`
- Runs a local custom ML model file in this service
- Returns urgency score on 0-10 scale

### Model configuration

Configured in `src/main/resources/application.properties`:

```properties
urgency.embedding-provider=local
urgency.model.dir=model
```

Provider behavior:

- `urgency.embedding-provider=local` uses DJL with `sentence-transformers/all-MiniLM-L6-v2` (384 dim).
- `urgency.embedding-provider=openai` uses LangChain4j OpenAI `text-embedding-3-small` (1536 dim).

Expected scorer model file:

- local: `services/urgency/model/model-scorer-local.dnet`
- openai: `services/urgency/model/model-scorer-openai.dnet`

Important: the model file provider must match the embedding provider used at inference time, and should be trained with the same embedding model setup from `urgency-training-pipeline`.

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

Select provider explicitly when running standalone:

```bash
# OpenAI mode
java -Durgency.embedding-provider=openai -jar target/quarkus-app/quarkus-run.jar

# Local mode
java -Durgency.embedding-provider=local -jar target/quarkus-app/quarkus-run.jar
```

Startup fail-fast behavior:
- If `openai` mode is selected but API key/config is missing, service startup aborts with a clear error.
- If the required provider model file (`*-openai.dnet` or `*-local.dnet`) is missing/ambiguous in `urgency.model.dir`, startup aborts with a clear error.

## Example

```bash
curl -X POST http://localhost:8086/api/urgency/v1/score \
  -H "Content-Type: application/json" \
  -d '{"complaint":"I brushed my hair today"}'
```
