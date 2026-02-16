## API Contracts

This document lists the primary HTTP endpoints exposed by the MedicalAppointment Ticketing System, with **`curl` examples** and **example responses**.

Unless otherwise noted:
- **Base URL**: `http://localhost:8080`
- **API prefix**: `/api`
- **Content type**: `application/json`
- Actor/persona headers:
  - `X-Actor-Id`
  - `X-Actor-Role`
  - `X-Actor-Team` (one of: `dispatch`, `billing`, `reschedule`, `engineering`)

For brevity, responses are **trimmed** to the most relevant fields.

---

### 1. Incoming Requests

#### 1.1 Create Incoming Request (User Complaint)

- **HTTP**: `POST /api/incoming-requests`

```bash
curl -X POST http://localhost:8080/api/incoming-requests \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch" \
  -d '{
    "userId": "user-123",
    "channel": "EMAIL",
    "rawText": "I was charged twice for my last appointment."
  }'
```

**Example Response** (`201 Created`):

```json
{
  "id": 1251,
  "userId": "user-123",
  "channel": "EMAIL",
  "rawText": "I was charged twice for my last appointment.",
  "status": "NEW",
  "createdAt": "2026-01-28T10:17:12.345Z"
}
```

#### 1.2 List Incoming Requests (Dispatcher Inbox)

- **HTTP**: `GET /api/incoming-requests?status=NEW` (or omitted for dispatcher inbox view)

```bash
curl "http://localhost:8080/api/incoming-requests" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch"
```

**Example Response** (`200 OK`):

```json
[
  {
    "id": 1251,
    "userId": "user-123",
    "channel": "EMAIL",
    "rawText": "I was charged twice for my last appointment.",
    "status": "AI_TRIAGE_FAILED",
    "createdAt": "2026-01-28T10:17:12.345Z"
  }
]
```

---

### 2. Dispatch

#### 2.1 Submit Ticket From Dispatch

- **HTTP**: `POST /api/dispatch/submit-ticket`

```bash
curl -X POST http://localhost:8080/api/dispatch/submit-ticket \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch" \
  -d '{
    "incomingRequestId": 1251,
    "ticketType": "BILLING_REFUND",
    "urgencyFlag": true,
    "urgencyScore": 8.0
  }'
```

**Example Response** (`201 Created`):

```json
{
  "id": 205,
  "userId": "user-123",
  "originalRequest": "I was charged twice for my last appointment.",
  "ticketType": "BILLING_REFUND",
  "status": "FROM_DISPATCH",
  "source": "MANUAL",
  "assignedTeam": "billing",
  "assignedTo": "billing-user1",
  "urgencyFlag": true,
  "urgencyScore": 8.0,
  "rollbackAllowed": false,
  "createdAt": "2026-01-28T10:18:00.000Z"
}
```

---

### 3. Tickets

#### 3.1 Create Manual Ticket

- **HTTP**: `POST /api/tickets/manual`

```bash
curl -X POST http://localhost:8080/api/tickets/manual \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing" \
  -d '{
    "userId": "user-123",
    "originalRequest": "Customer walked in and requested invoice copy.",
    "ticketType": "BILLING_OTHER",
    "urgencyFlag": false,
    "urgencyScore": 3.0
  }'
```

**Example Response** (`201 Created`):

```json
{
  "id": 210,
  "userId": "user-123",
  "originalRequest": "Customer walked in and requested invoice copy.",
  "ticketType": "BILLING_OTHER",
  "status": "FROM_DISPATCH",
  "source": "MANUAL",
  "assignedTeam": "billing",
  "assignedTo": "billing-user1",
  "urgencyFlag": false,
  "urgencyScore": 3.0
}
```

#### 3.2 Create Ticket From AI (Direct)

- **HTTP**: `POST /api/tickets/from-ai`

```bash
curl -X POST http://localhost:8080/api/tickets/from-ai \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch" \
  -d '{
    "userId": "user-123",
    "originalRequest": "The reschedule button is disabled on my appointment.",
    "ticketType": "BUG_APP",
    "urgencyScore": 5.0,
    "aiConfidencePercent": 85,
    "relatedTicketIds": [912, 847],
    "policyCitations": [
      {
        "documentName": "Known_Bugs_Limitations.txt",
        "documentLink": "/documents/Known_Bugs_Limitations.txt",
        "citation": "BUG-001 ...",
        "score": 0.82,
        "rbacTeams": ["dispatch", "engineering"]
      }
    ]
  }'
```

**Example Response** (`201 Created`):

```json
{
  "id": 220,
  "ticketType": "BUG_APP",
  "status": "FROM_AI",
  "source": "AI",
  "assignedTeam": "engineering",
  "assignedTo": "engineering-user1",
  "urgencyScore": 5.0,
  "aiConfidence": 0.85,
  "rollbackAllowed": true,
  "aiPayloadJson": "{\"relatedTicketIds\":[912,847],\"policyCitations\":[...]}"
}
```

#### 3.3 List Tickets

- **HTTP**: `GET /api/tickets?view=inbox|team|mine&team=...&user=...`

Examples:

```bash
# All tickets (inbox)
curl "http://localhost:8080/api/tickets?view=inbox" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch"

# My team tickets
curl "http://localhost:8080/api/tickets?view=team&team=billing" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing"

# My tickets
curl "http://localhost:8080/api/tickets?view=mine&user=billing-user1" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing"
```

**Example Response** (`200 OK`):

```json
[
  {
    "id": 205,
    "ticketType": "BILLING_REFUND",
    "status": "IN_PROGRESS",
    "assignedTeam": "billing",
    "assignedTo": "billing-user1",
    "urgencyFlag": true,
    "urgencyScore": 8.0,
    "createdAt": "2026-01-28T10:18:00.000Z"
  }
]
```

#### 3.4 Get Ticket Detail

- **HTTP**: `GET /api/tickets/{id}`

```bash
curl "http://localhost:8080/api/tickets/205" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing"
```

**Example Response** (`200 OK`):

```json
{
  "id": 205,
  "userId": "user-123",
  "originalRequest": "I was charged twice for my last appointment.",
  "ticketType": "BILLING_REFUND",
  "status": "IN_PROGRESS",
  "source": "MANUAL",
  "assignedTeam": "billing",
  "assignedTo": "billing-user1",
  "urgencyFlag": true,
  "urgencyScore": 8.0,
  "aiPayloadJson": "{\"relatedTicketIds\":[912],\"policyCitations\":[...]}",
  "comments": [
    {
      "authorId": "billing-user1",
      "body": "Contacted user, waiting for bank confirmation.",
      "createdAt": "2026-01-28T10:25:00.000Z"
    }
  ]
}
```

#### 3.5 Accept Ticket

- **HTTP**: `POST /api/tickets/{id}/accept?userId=...`

```bash
curl -X POST "http://localhost:8080/api/tickets/205/accept?userId=billing-user1" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing"
```

**Example Response** (`200 OK`):

```json
{
  "id": 205,
  "status": "TRIAGED",
  "assignedTo": "billing-user1"
}
```

#### 3.6 Reject AI Ticket and Return to Dispatch

- **HTTP**: `POST /api/tickets/{id}/reject-and-return-to-dispatch`

```bash
curl -X POST "http://localhost:8080/api/tickets/220/reject-and-return-to-dispatch" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing"
```

**Example Response** (`200 OK`):

```json
{
  "id": 220,
  "status": "RETURNED_TO_DISPATCH",
  "assignedTeam": "dispatch",
  "assignedTo": null
}
```

#### 3.7 Update Ticket Status

- **HTTP**: `POST /api/tickets/{id}/status`

```bash
curl -X POST "http://localhost:8080/api/tickets/205/status" \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing" \
  -d '{ "status": "COMPLETED" }'
```

**Example Response** (`200 OK`):

```json
{
  "id": 205,
  "status": "COMPLETED"
}
```

#### 3.8 Add Comment to Ticket

- **HTTP**: `POST /api/tickets/{id}/comments`

```bash
curl -X POST "http://localhost:8080/api/tickets/205/comments" \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: billing-user1" \
  -H "X-Actor-Team: billing" \
  -d '{
    "authorId": "billing-user1",
    "body": "Refund processed, user notified via email."
  }'
```

**Example Response** (`201 Created`):

```json
{
  "ticketId": 205,
  "authorId": "billing-user1",
  "body": "Refund processed, user notified via email.",
  "createdAt": "2026-01-28T10:30:00.000Z"
}
```

---

### 4. Events

#### 4.1 Get Recent Events

- **HTTP**: `GET /api/events/recent?since={timestamp}&limit={n}`

```bash
curl "http://localhost:8080/api/events/recent?limit=50" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch"
```

**Example Response** (`200 OK`):

```json
[
  {
    "id": 501,
    "eventType": "TICKET_CREATED",
    "severity": "INFO",
    "source": "ticketing-api",
    "message": "Ticket #205 created from dispatch",
    "ticketId": 205,
    "incomingRequestId": 1251,
    "createdAt": "2026-01-28T10:18:00.000Z"
  }
]
```

---

### 5. Triage Worker (Manual Trigger)

Normally, incoming requests are triaged automatically, but you can trigger a full run manually.

- **HTTP**: `POST /api/triage-worker/process`

```bash
curl -X POST http://localhost:8080/api/triage-worker/process \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Team: dispatch"
```

**Example Response** (`200 OK`):

```json
{
  "processedCount": 5,
  "failedCount": 0
}
```

---

### 6. Documents (Proxy – RBAC-Aware Display in UI)

The helpdesk UI does **not** call the external documents service directly (to avoid CORS).  
Instead, it uses a **proxy endpoint** in this service, and the UI applies RBAC based on `rbacTeams` and the current persona/team.

#### 6.1 Proxy: Get Document Content

- **HTTP**: `GET /api/documents/content/{documentName}`
- **Proxies to**: `http://localhost:8084/api/documents/content/{documentName}`

```bash
curl "http://localhost:8080/api/documents/content/Known_Bugs_Limitations.txt" \
  -H "X-Actor-Id: engineering-user1" \
  -H "X-Actor-Team: engineering"
```

**Example Response** (`200 OK`, `text/plain`):

```text
Purpose
This document lists confirmed application defects that are actively tracked or under investigation.
...
BUG-001
Title: Reschedule Button Disabled on Existing Appointments
Symptoms:
Users report that the "Reschedule" button is disabled...
```

> Note: The external documents API returns JSON of the form:
>
> ```json
> { "content": ".... full document text ...." }
> ```
>
> The proxy and frontend extract the `content` field and display it as plain text.

---

### 7. External Documents Service (Reference Only)

This service is **outside** the Quarkus app, but is included here for completeness.

- **Base URL**: `http://localhost:8084`
- **Endpoint**: `GET /api/documents/content/{documentName}`

```bash
curl "http://localhost:8084/api/documents/content/Approved_Response_Templates.txt"
```

**Example Response** (`200 OK`):

```json
{
  "content": "Dear {{userName}},\n\nThank you for contacting MedicalAppointment support...\n"
}
```

The ticketing UI should **only show document links for teams listed in `rbacTeams`** returned by AI triage; the backend filters `policyCitations` per actor team before sending data to the browser.

