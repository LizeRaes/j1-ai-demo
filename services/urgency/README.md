# Urgency Service

Direct HTTP urgency scoring service used by `ai-triage`.

## Current behavior

- Endpoint: `POST /api/urgency/v1/score`
- Port: `8086`
- Placeholder response: `{"score":0.7}`

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
