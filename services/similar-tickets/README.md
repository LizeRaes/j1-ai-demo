# Ticket Similarity Service

## Prerequisites

- Java 25
- Maven 3.8+
- Docker (for Qdrant)

## Setup

Configure the OpenAI API key before starting the service:

- create `config/config-prod.yaml` with `openai.api-key`
- OR: set `OPENAI_API_KEY` in your environment

1. **Start the Oracle AI database:**
   ```bash
   docker-compose up -d
   ```

2. **Build and run the application:**

With JDK25
```bash
mvn clean verify
java -DDemoData=true -jar target/similar-tickets.jar
```

`-DDemoData=true` wipes existing vectors and loads demo tickets (about 150), then embeds them.

> [!WARNING]
> If `OPENAI_API_KEY` is set, startup with demo data triggers embedding API calls and incurs a small cost, approx. `$0.0010-$0.0022` with current embedding model.
> To start without demo loading/vectorization: `java -jar target/similar-tickets.jar`

The application will run on port **8082**.

**Important:** Make sure Oracle AI 26ai is running before starting the application, otherwise you'll get connection errors.

### Running Integration Tests

By default, integration tests are disabled unless you supply some system variables.
If you have an OpenAPI key and an Oracle Database connection, you may run the tests for the application using the following command:

```bash
mvn clean -Dopenai.api-key=<key> -D'data.sources.sql[0].provider.ucp.url'=jdbc:oracle:thin:<DB_URL>:<PORT>/<DB_NAME> verify
```

## Web Dashboard

The service includes a web dashboard for monitoring tickets and activity logs.

**Access the dashboard at:** http://localhost:8082

The dashboard features:
- **Left pane (1/3 width)**: Real-time activity logs showing:
  - Ticket upsert operations
  - Similarity search requests (with similarity scores for each ticket)
  - Delete operations
- **Right pane (2/3 width)**: Scrollable table displaying:
  - Ticket ID
  - Ticket Type
  - Original Text (full text with wrapping)
  - Vector preview (first 10 dimensions)
  - Latest tickets appear first

The dashboard automatically refreshes every second to show the latest data.

## API Endpoints

### 1. Upsert Embedding
**POST** `/api/similarity/tickets/upsert`

Creates or updates an embedding for a ticket.

**Request:**
```json
{
  "ticketId": 912,
  "ticketType": "BUG_APP",
  "text": "The reschedule button is disabled on my appointment."
}
```

**Response:**
```json
{
  "status": "OK"
}
```

### 2. Delete Embedding
**POST** `/api/similarity/tickets/delete`

Deletes an embedding for a ticket (idempotent).

**Request:**
```json
{
  "ticketId": 912
}
```

**Response:**
```json
{
  "status": "OK"
}
```

### 3. Similarity Search
**POST** `/api/similarity/tickets/search`

Searches for the most similar tickets across all ticket types by embedding the query text. The specified ticket is excluded from results.

**Request:**
```json
{
  "text": "reschedule button disabled",
  "ticketId": 912,
  "maxResults": 5,
  "minScore": 0.7
}
```

**Fields:**
- `text` (required): Query text to search for similar tickets
- `ticketId` (required): This ticket will be excluded from results
- `maxResults` (optional): Maximum number of results (default: 5)
- `minScore` (optional): Minimum similarity score threshold (default: 0.0)
- `ticketType` (optional, deprecated): Ignored - search is performed across all ticket types

**Response:**
```json
{
  "relatedTicketIds": [150, 183, 167, 171, 180]
}
```

### 4. Get All Tickets (for dashboard)
**GET** `/api/similarity/tickets/all`

Retrieves all stored tickets with their embeddings and metadata.

**Response:**
```json
{
  "tickets": [
    {
      "ticketId": 912,
      "ticketType": "BUG_APP",
      "text": "The reschedule button is disabled on my appointment.",
      "vector": [0.1234, -0.5678, ...]
    }
  ]
}
```

### 5. Get Activity Logs (for dashboard)
**GET** `/api/similarity/tickets/logs`

Retrieves activity logs for the dashboard.

**Response:**
```json
{
  "logs": [
    {
      "message": "Received ticket #912 via upsert endpoint",
      "type": "upsert",
      "timestamp": 1706177897000
    }
  ]
}
```

## Testing with cURL

Once the service is running on port 8082, you can test it with the following curl commands:

### 1. Upsert a Ticket Embedding

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 940,
    "ticketType": "BUG_APP",
    "text": "The reschedule button is disabled on my appointment."
  }'
```

**Expected Response:**
```json
{
  "status": "OK"
}
```

### 2. Search for Similar Tickets

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{
    "text": "reschedule button disabled",
    "ticketId": 917,
    "maxResults": 5
  }'
```

**Expected Response:**
```json
{
  "relatedTicketIds": [912,940,150,183,167]
}
```

### 3. Delete a Ticket Embedding

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/delete \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 912
  }'
```

**Expected Response:**
```json
{
  "status": "OK"
}
```

### Complete Test Flow

Here's a complete test sequence:

```bash
# 1. Create/update embeddings for multiple tickets
curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 912, "ticketType": "BUG_APP", "text": "The reschedule button is disabled on my appointment."}'

curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 847, "ticketType": "BUG_APP", "text": "Cannot reschedule appointment, button not working"}'

curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 903, "ticketType": "BUG_APP", "text": "Reschedule functionality is broken"}'

# 2. Search for similar tickets (excluding ticket 912 from results)
curl -X POST http://localhost:8082/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{"text": "reschedule button disabled", "maxResults": 5, "id": 912}'

# 3. Delete a ticket embedding
curl -X POST http://localhost:8082/api/similarity/tickets/delete \
  -H "Content-Type: application/json" \
  -d '{"ticketId": 912}'
```

## Data Persistence

- **Embeddings are stored in the Oracle database** and persist across application restarts
- **Ticket metadata** (ticketId, ticketType, and original text) is stored in Qdrant payload
- The in-memory `TicketStore` is used for fast access but data is always loaded from the database on startup
- Data is persisted in Docker volumes (see `docker-compose.yml`)

## Configuration

Edit `src/main/resources/application.yaml` to configure:
- Qdrant host and port (default: localhost:6334)
- Collection name (default: ticket-embeddings)
- OpenAI API key (required for embeddings)

## Technology Stack

- **Helidon 4.3.3** - Java framework
- **Helidon LangChain4J Extension** - Automatic EmbeddingModel configuration via YAML
- **LangChain4j** - Embedding generation and OracleEmbeddingStoreConfig
- **OpenAI text-embedding-3-large** - Embedding model (3072-dimensional vectors)
- **Oracle AI 26ai Database** - Vector database for similarity search

## Embedding Model

The service uses **OpenAI's text-embedding-3-large** model to generate embeddings:
- **Dimensions**: 3072
- **Model**: text-embedding-3-large
- **Provider**: OpenAI (requires API key)
- **Distance Metric**: Cosine similarity (configured in Qdrant)

The model embeds only the `text` field from ticket requests. No comments or metadata are included in the embeddings.

### Configuration

```yaml
openai:
  api-key: ${OPENAI_API_KEY=demo}
  embedding-model: "text-embedding-3-large"
```

The embedding model can be changed via:
```yaml
openai:
  embedding-model: "text-embedding-3-large"
```

## Architecture Notes

- **API key behavior**: read framework config value first (including optional `config/config-prod.yaml` when profile `prod` is active); if empty or `demo`, fallback to `OPENAI_API_KEY`; if still missing/`demo`, startup fails fast.
- **Embedding Generation**: Text is embedded on-the-fly using OpenAI's API when upserting or searching
- **Vector Storage**: 3072-dimensional vectors stored in Qdrant with cosine similarity
- **Metadata Storage**: Ticket ID, type, and original text are stored in Qdrant payload for retrieval
- **Search**: Returns the most similar tickets across all ticket types, excluding only the specified ticket
- **Idempotency**: All operations (upsert, delete) are idempotent - safe to retry

## Troubleshooting

**Qdrant Connection Errors:**
- Ensure the database is running: `docker-compose ps`
- Check Qdrant logs: `docker-compose logs oracle`
- Verify port 1521 is accessible: `telnet localhost 1521`

**OpenAI API Errors:**
- Verify `OPENAI_API_KEY` is set correctly
- Check API key has sufficient credits/quota
- Ensure network can reach OpenAI API


## Try metrics

```
# Prometheus Format
curl -s -X GET http://localhost:8082/observe/metrics
# TYPE base:gc_g1_young_generation_count gauge
. . .

# JSON Format
curl -H 'Accept: application/json' -X GET http://localhost:8082/observe/metrics
{"base":...
. . .
```


## Try health

This example shows the basics of using Helidon SE Health. It uses the
set of built-in health checks that Helidon provides plus defines a
custom health check.

Note the port number reported by the application.

Probe the health endpoints:

```bash
curl -X GET http://localhost:8082/observe/health
curl -X GET http://localhost:8082/observe/health/ready
```


## Building the Docker Image

```
docker build -t similar-tickets .
```

## Running the Docker Image

```
docker run --rm -p 8080:8080 similar-tickets:latest
```

Exercise the application as described above.


## Run the application in Kubernetes

If you don’t have access to a Kubernetes cluster, you can [install one](https://helidon.io/docs/latest/#/about/kubernetes) on your desktop.

### Verify connectivity to cluster

```
kubectl cluster-info                        # Verify which cluster
kubectl get pods                            # Verify connectivity to cluster
```

### Deploy the application to Kubernetes

```
kubectl create -f app.yaml                              # Deploy application
kubectl get pods                                        # Wait for quickstart pod to be RUNNING
kubectl get service  similar-tickets                     # Get service info
kubectl port-forward service/similar-tickets 8081:8080   # Forward service port to 8081
```

You can now exercise the application as you did before but use the port number 8081.

After you’re done, cleanup.

```
kubectl delete -f app.yaml
```