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

Expected scorer model file:

- local: `services/urgency/model/model-scorer-local.dnet`
- openai: `services/urgency/model/model-scorer-openai.dnet`

## Build

```bash
mvn package
```

## Run

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

## Example

```bash
curl -X POST http://localhost:8086/api/urgency/v1/score \
  -H "Content-Type: application/json" \
  -d '{"complaint":"I brushed my hair today"}'
```
