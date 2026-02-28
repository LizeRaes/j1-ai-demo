## Ticket Similarity Service API Contracts

Base URL (dev): `http://localhost:8082`

---

### 1. Upsert Ticket Embedding

- **Method**: `POST`
- **Path**: `/api/similarity/tickets/upsert`
- **Description**: Create or update an embedding for a ticket.

**Request Body**

```json
{
  "ticketId": 912,
  "ticketType": "BUG_APP",
  "text": "The reschedule button is disabled on my appointment."
}
```

**Curl Example**

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 912,
    "ticketType": "BUG_APP",
    "text": "The reschedule button is disabled on my appointment."
  }'
```

**Response (200)**

```json
{
  "status": "OK"
}
```

---

### 2. Delete Ticket Embedding

- **Method**: `DELETE`
- **Path**: `/api/similarity/tickets/delete`
- **Description**: Delete a ticket embedding (idempotent; no-op if it does not exist).

**Curl Example**

```bash
curl -X 'DELETE' \
  'http://localhost:8082/api/similarity/tickets/delete/127' \
  -H 'accept: application/json'
```

**Response (200)**

```json
{
  "status": "OK"
}
```

---

### 3. Similarity Search

- **Method**: `POST`
- **Path**: `/api/similarity/tickets/search`
- **Description**: Search for tickets similar to the given text across all ticket types. The specified ticketId is excluded from results.

**Request Body**

```json
{
  "text": "reschedule button disabled",
  "ticketId": 912,
  "maxResults": 5,
  "minScore": 0.7
}
```

**Field Semantics**
- `text` (string, required): Query text to embed and search with.
- `ticketId` (long, required): Ticket to exclude from results.
- `maxResults` (int, optional): Max number of similar tickets to return (default: 5).
- `minScore` (double, optional): Minimum similarity score (default: 0.0).
- `ticketType` (string, optional): Currently ignored; search is global across types.

**Curl Example**

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{
    "text": "reschedule button disabled",
    "ticketId": 912,
    "maxResults": 5,
    "minScore": 0.7
  }'
```

**Response (200)**

```json
{
  "relatedTicketIds": [847, 903, 940]
}
```

---

### 4. Get All Tickets (Dashboard Data)

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/all`
- **Description**: Retrieve all tickets known to the system with their latest text and embedding vectors.

**Curl Example**

```bash
curl -X GET http://localhost:8082/api/similarity/tickets/all
```

**Response (200)**

```json
{
  "tickets": [
    {
      "ticketId": 912,
      "ticketType": "BUG_APP",
      "text": "The reschedule button is disabled on my appointment.",
      "vector": [0.1234, -0.5678, 0.9012]
    },
    {
      "ticketId": 847,
      "ticketType": "BUG_APP",
      "text": "Cannot reschedule appointment, button not working",
      "vector": [0.1111, -0.2222, 0.3333]
    }
  ]
}
```

Notes:
- `vector` contains the full embedding; for brevity only the first few dimensions are shown here.

---

### 5. Get Activity Logs

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/logs`
- **Description**: Fetch activity logs used by the dashboard (upserts, searches, deletes).

**Curl Example**

```bash
curl -X GET http://localhost:8082/api/similarity/tickets/logs
```

**Response (200)**

```json
{
  "logs": [
    {
      "message": "Received ticket #912 via upsert endpoint",
      "type": "upsert",
      "timestamp": 1706177897000
    },
    {
      "message": "Returned 3 similar tickets: [847 (0.932), 903 (0.887), 940 (0.842)]",
      "type": "search",
      "timestamp": 1706177902000
    }
  ]
}
```

---

### 6. Get Config

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/config`
- **Description**: Return simple UI-related configuration used by the dashboard.

**Curl Example**

```bash
curl -X GET http://localhost:8082/api/similarity/tickets/config
```

**Response (200)**

```json
{
  "defaultZoom": 100
}
```

---

### Error Semantics

- **Validation errors** (e.g., missing `ticketId` or `text`) are thrown as `400 Bad Request` by the framework, with a JSON body describing the error (Quarkus default error payload).
- All endpoints return JSON and expect `Content-Type: application/json` for bodies.

