## API Contracts

This document lists all public HTTP endpoints exposed by the service, with **request**, **curl example**, and **example response**.

Base URL (dev): `http://localhost:8084`

---

## Documents API (`/api/documents`)

### 1. Search Documents

- **Method**: `POST`
- **Path**: `/api/documents/search`
- **Description**: Search for similar document chunks based on ticket text.

**Request body**

```json
{
  "originalText": "reschedule button disabled",
  "maxResults": 5,
  "minScore": 0.3
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/documents/search \
  -H "Content-Type: application/json" \
  -d '{
    "originalText": "reschedule button disabled",
    "maxResults": 5,
    "minScore": 0.3
  }'
```

**Example response**

```json
{
  "results": [
    {
      "documentName": "Approved_Response_Templates.txt",
      "documentLink": "/documents/Approved_Response_Templates.txt",
      "citation": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out...",
      "score": 0.892,
      "rbacTeams": ["dispatch", "billing", "reschedule"]
    }
  ]
}
```

---

### 2. Upsert Document

- **Method**: `POST`
- **Path**: `/api/documents/upsert`
- **Description**: Create or update a document with content and RBAC teams. If `rbacTeams` is omitted or empty, the document is company-wide.

**Request body**

```json
{
  "documentName": "New_Policy.txt",
  "content": "This is the document content...",
  "rbacTeams": ["billing", "dispatch"]
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/documents/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "New_Policy.txt",
    "content": "This is the document content...",
    "rbacTeams": ["billing", "dispatch"]
  }'
```

**Example response**

```json
{
  "status": "OK"
}
```

---

### 3. Delete Document

- **Method**: `DELETE`
- **Path**: `/api/documents/delete/{documentName}`
- **Description**: Delete a document and all its embeddings (idempotent).

**Curl**

```bash
curl -X POST http://localhost:8084/api/documents/delete/Old_Policy.txt \
  -H "Content-Type: application/json"
```

**Example response**

```json
{
  "status": "OK"
}
```

---

### 4. Update Document RBAC

- **Method**: `POST`
- **Path**: `/api/rbac/update`
- **Description**: Update RBAC teams for a document. Empty `rbacTeams` means company-wide.

**Request body**

```json
{
  "documentName": "Approved_Response_Templates.txt",
  "rbacTeams": ["dispatch", "billing"]
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/rbac/update \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "Approved_Response_Templates.txt",
    "rbacTeams": ["dispatch", "billing"]
  }'
```

**Example response**

```json
{
  "status": "OK"
}
```

---

### 5. Get All Documents

- **Method**: `GET`
- **Path**: `/api/documents/all`
- **Description**: List all known documents with links and RBAC teams.

**Curl**

```bash
curl http://localhost:8084/api/documents/all
```

**Example response**

```json
{
  "documents": [
    {
      "documentName": "Approved_Response_Templates.txt",
      "documentLink": "/documents/Approved_Response_Templates.txt",
      "rbacTeams": ["dispatch", "billing", "reschedule"]
    },
    {
      "documentName": "New_Policy.txt",
      "documentLink": "/documents/New_Policy.txt",
      "rbacTeams": ["billing", "dispatch"]
    }
  ]
}
```

---

### 6. Get Activity Logs (Documents)

- **Method**: `GET`
- **Path**: `/api/documents/logs`
- **Description**: Retrieve activity logs (upserts, deletes, searches, RBAC updates).

**Curl**

```bash
curl http://localhost:8084/api/documents/logs
```

**Example response**

```json
{
  "logs": [
    {
      "message": "Document search request: \"reschedule button...\"",
      "type": "search",
      "timestamp": 1706177897000
    }
  ]
}
```

---

### 7. Get Document Content

- **Method**: `GET`
- **Path**: `/api/documents/content/{documentName}`
- **Description**: Get the full raw `.txt` content for a document.

**Curl**

```bash
curl "http://localhost:8084/api/documents/content/Approved_Response_Templates.txt"
```

**Example response**

```json
{
  "content": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out..."
}
```

---

### 8. Get All Chunks & Embeddings

- **Method**: `GET`
- **Path**: `/api/documents/chunks`
- **Description**: Retrieve all stored chunks with their vectors (for inspection/diagnostics).

**Curl**

```bash
curl http://localhost:8084/api/documents/chunks
```

**Example response**

```json
{
  "chunks": [
    {
      "documentName": "Approved_Response_Templates.txt",
      "chunkIndex": 0,
      "text": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out...",
      "vector": [0.0123, -0.0045, 0.0345]
    }
  ]
}
```

---

### 9. Get Config (Documents UI)

- **Method**: `GET`
- **Path**: `/api/documents/config`
- **Description**: Retrieve UI-related configuration.

**Curl**

```bash
curl http://localhost:8084/api/documents/config
```

**Example response**

```json
{
  "defaultZoom": 100
}
```

---

## Legacy Ticket Similarity API (`/api/similarity/tickets`)

These endpoints back the old ticket similarity UI. They are still available but secondary to the documents API.

### 10. Upsert Ticket

- **Method**: `POST`
- **Path**: `/api/similarity/tickets/upsert`
- **Description**: Create or update a ticket and its embedding.

**Request body**

```json
{
  "ticketId": 12345,
  "ticketType": "incident",
  "text": "Customer cannot reschedule appointment via portal"
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/similarity/tickets/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 12345,
    "ticketType": "incident",
    "text": "Customer cannot reschedule appointment via portal"
  }'
```

**Example response**

```json
{
  "status": "OK"
}
```

---

### 11. Delete Ticket

- **Method**: `POST`
- **Path**: `/api/similarity/tickets/delete`
- **Description**: Delete a ticket and its embedding (idempotent).

**Request body**

```json
{
  "ticketId": 12345
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/similarity/tickets/delete \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 12345
  }'
```

**Example response**

```json
{
  "status": "OK"
}
```

---

### 12. Search Similar Tickets

- **Method**: `POST`
- **Path**: `/api/similarity/tickets/search`
- **Description**: Search for similar tickets based on text; returns related ticket IDs.

**Request body**

```json
{
  "ticketId": 12345,
  "text": "Customer cannot reschedule appointment via portal",
  "maxResults": 5,
  "minScore": 0.3
}
```

**Curl**

```bash
curl -X POST http://localhost:8084/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 12345,
    "text": "Customer cannot reschedule appointment via portal",
    "maxResults": 5,
    "minScore": 0.3
  }'
```

**Example response**

```json
{
  "relatedTicketIds": [12340, 12200, 11890]
}
```

---

### 13. Get All Tickets

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/all`
- **Description**: List all stored tickets with text and vector (first N dims typically used in UI).

**Curl**

```bash
curl http://localhost:8084/api/similarity/tickets/all
```

**Example response**

```json
{
  "tickets": [
    {
      "ticketId": 12345,
      "ticketType": "incident",
      "originalText": "Customer cannot reschedule appointment via portal",
      "vector": [0.0123, -0.0045, 0.0345]
    }
  ]
}
```

---

### 14. Get Activity Logs (Tickets)

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/logs`
- **Description**: Retrieve ticket-related activity logs.

**Curl**

```bash
curl http://localhost:8084/api/similarity/tickets/logs
```

**Example response**

```json
{
  "logs": [
    {
      "message": "Received ticket #12345 via upsert endpoint",
      "type": "upsert",
      "timestamp": 1706177897000
    }
  ]
}
```

---

### 15. Get Config (Tickets UI)

- **Method**: `GET`
- **Path**: `/api/similarity/tickets/config`
- **Description**: Retrieve UI configuration for the legacy tickets UI.

**Curl**

```bash
curl http://localhost:8084/api/similarity/tickets/config
```

**Example response**

```json
{
  "defaultZoom": 100
}
```

