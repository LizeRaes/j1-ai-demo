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
- **Path**: `/api/documents/rbac/update`
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
curl -X POST http://localhost:8084/api/documents/rbac/update \
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