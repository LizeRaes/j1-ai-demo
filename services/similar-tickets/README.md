# Ticket Similarity Service

The Ticket Similarity Service stores ticket embeddings in Oracle AI Database and searches for related historical tickets by vector similarity. It also serves a small dashboard at `/` for inspecting recent logs and stored ticket vectors.

## Prerequisites

- Java 25+
- Maven 3.8+
- Docker, for the local Oracle database
- `OPENAI_API_KEY`, required when generating embeddings

## Setup

Start the local Oracle database:

```bash
docker-compose up -d
```

Build the service:

```bash
mvn clean verify
```

Run without demo data:

```bash
java -jar target/similarity.jar
```

Run with bundled demo data:

```bash
java -DDemoData=true -jar target/similarity.jar
```

`-DDemoData=true` deletes existing vectors from the configured embedding table, loads the configured demo-data directory, and embeds those tickets. The bundled classpath demo data currently contains 90 tickets.

> [!WARNING]
> Demo-data loading calls the configured embedding provider. With OpenAI enabled, this consumes API quota.

The service listens on port `8082` by default.

## Configuration

Edit `src/main/resources/application.yaml` or provide equivalent Helidon config.

```yaml
similarity:
  tickets:
    base-path: "/api/similarity/tickets"
    search:
      max-results: 5
      min-score: 0.0
    demo-data:
      directory: "demo-data"
```

Important settings:

- `similarity.tickets.base-path`: API base path. Defaults to `/api/similarity/tickets`.
- `similarity.tickets.search.max-results`: default search result limit when request `maxResults` is omitted.
- `similarity.tickets.search.min-score`: default search score threshold when request `minScore` is omitted.
- `similarity.tickets.demo-data.directory`: filesystem or classpath directory containing demo `*.json` files. Defaults to bundled `demo-data` resources.
- `langchain4j.providers.open-ai.api-key`: defaults to `${OPENAI_API_KEY}`.
- `langchain4j.models.ticket-embedding-model`: embedding model config. The default model is `text-embedding-3-large`.
- `langchain4j.embedding-stores.oracle-embedding-store.embedding-table`: Oracle embedding table settings.
- `data.sources.sql[0].provider.ucp`: Oracle datasource connection settings.
- `ui.font.zoom.default`: default dashboard zoom returned by `/config`.

## Web Dashboard

Open the dashboard at:

```text
http://localhost:8082
```

The dashboard shows:

- Activity logs from `/logs`.
- Ticket rows from `/all`, including ticket ID, ticket type, text, and a vector preview.
- Automatic refresh every second.
- Configurable default zoom from `/config`.

## API Endpoints

The examples below use the default base path `/api/similarity/tickets`. If `similarity.tickets.base-path` is changed, replace that prefix in the examples.

### Upsert Embedding

`POST /api/similarity/tickets/upsert`

```json
{
  "ticketId": 912,
  "ticketType": "BUG_APP",
  "text": "The reschedule button is disabled on my appointment."
}
```

Response:

```json
{
  "status": "OK"
}
```

### Delete Embedding

`DELETE /api/similarity/tickets/delete/{ticketId}`

```bash
curl -X DELETE http://localhost:8082/api/similarity/tickets/delete/912 \
  -H "Accept: application/json"
```

Response:

```json
{
  "status": "OK"
}
```

### Similarity Search

`POST /api/similarity/tickets/search`

```json
{
  "text": "reschedule button disabled",
  "ticketId": 912,
  "maxResults": 5,
  "minScore": 0.7
}
```

Fields:

- `text` is required.
- `ticketId` is required and is excluded from results.
- `maxResults` is optional and defaults to `similarity.tickets.search.max-results`.
- `minScore` is optional and defaults to `similarity.tickets.search.min-score`.
- `ticketType` is accepted for compatibility but ignored; search is global across ticket types.

Response:

```json
{
  "relatedTicketIds": [150, 183, 167, 171, 180]
}
```

### Get All Tickets

`GET /api/similarity/tickets/all`

Returns stored ticket metadata and full embedding vectors.

```json
{
  "tickets": [
    {
      "ticketId": 912,
      "ticketType": "BUG_APP",
      "text": "The reschedule button is disabled on my appointment.",
      "vector": [0.1234, -0.5678]
    }
  ]
}
```

### Get Activity Logs

`GET /api/similarity/tickets/logs`

```json
{
  "logs": [
    {
      "message": "[INFO] Received ticket #912 via upsert endpoint",
      "type": "upsert",
      "timestamp": 1706177897000
    }
  ]
}
```

### Get UI Config

`GET /api/similarity/tickets/config`

```json
{
  "defaultZoom": 100
}
```

## Testing with cURL

Upsert a ticket:

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 940, "ticketType": "BUG_APP", "text": "The reschedule button is disabled on my appointment."}'
```

Search for similar tickets:

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{"text": "reschedule button disabled", "ticketId": 917, "maxResults": 5}'
```

Delete a ticket embedding:

```bash
curl -X DELETE http://localhost:8082/api/similarity/tickets/delete/912 \
  -H "Accept: application/json"
```

## Tests

Run unit tests:

```bash
mvn test
```

Some HTTP/vector integration tests are skipped unless both an OpenAI key and Oracle JDBC URL are configured:

```bash
OPENAI_API_KEY=<key> \
mvn test -D'data.sources.sql[0].provider.ucp.url'=jdbc:oracle:thin:@localhost:1521/freepdb1
```

## Data Persistence

- Embeddings are stored in Oracle AI Database and persist across service restarts.
- Ticket metadata (`ticketId`, `ticketType`, and text) is stored in embedding metadata and text columns.
- `TicketStore` is in-memory and only contains tickets upserted during the current process. The `/all` endpoint also reads persisted tickets from Oracle.
- Docker volume behavior is controlled by `docker-compose.yml` and the Oracle image.

## Technology Stack

- Helidon SE 4.4.0
- Helidon LangChain4j integration
- LangChain4j
- OpenAI `text-embedding-3-large`
- Oracle AI Database vector storage

## Architecture Notes

- Text is embedded on upsert and search.
- Search returns IDs only, ordered by the vector store result order.
- Search is global across ticket types and excludes the request `ticketId`.
- Upsert deletes any previous embedding for the same ticket ID before adding the new embedding.
- Delete is idempotent.

## Observability

Metrics:

```bash
curl -s http://localhost:8082/observe/metrics
curl -H "Accept: application/json" http://localhost:8082/observe/metrics
```

Health:

```bash
curl http://localhost:8082/observe/health
curl http://localhost:8082/observe/health/ready
```

## Docker Image

Build:

```bash
docker build -t similar-tickets .
```

Run:

```bash
docker run --rm -p 8082:8082 -e OPENAI_API_KEY="$OPENAI_API_KEY" similar-tickets:latest
```
