## API Contracts

This document lists the primary API contracts used by the MediFlow AI triage flow, including request/response schemas and `curl` examples.

All JSON examples are illustrative; exact values (IDs, scores, text) will vary at runtime.

---

## AI Triage Service (this service)

### Classify Ticket

- **Method**: `POST`
- **URL**: `http://localhost:8081/api/triage/v1/classify`
- **Content-Type**: `application/json`

#### Request Body

```json
{
  "incomingRequestId": 123,
  "message": "The reschedule button is disabled on my appointment.",
  "ticketId": 912,
  "allowedTicketTypes": [
    {
      "type": "BILLING_REFUND",
      "description": "User is asking for a refund or billing reimbursement"
    },
    {
      "type": "SCHEDULING_CANCELLATION",
      "description": "User wants to cancel or reschedule an appointment"
    },
    {
      "type": "BUG_APP",
      "description": "Bug or error in the user-facing application or UI"
    },
    {
      "type": "OTHER",
      "description": "AI cannot confidently classify this request and a human dispatcher must decide"
    }
  ]
}
```

#### Success Response

```json
{
  "status": "OK",
  "ticketType": "BUG_APP",
  "urgencyScore": 5,
  "aiConfidencePercent": 85,
  "relatedTicketIds": [912, 847],
  "policyCitations": [
    {
      "documentName": "Known_Bugs_Limitations.txt",
      "documentLink": "/documents/Known_Bugs_Limitations.txt",
      "citation": "BUG-001\nTitle: Reschedule Button Disabled on Existing Appointments\n\nSymptoms:\nUsers report that the \"Reschedule\" button is disabled on the appointment details page...",
      "score": 0.8257503518005138,
      "rbacTeams": ["dispatch", "engineering"]
    }
  ]
}
```

#### Error Response

```json
{
  "status": "FAILED",
  "failReason": "AI_TRIAGE_FAILED: PARSE_FAILED: ...",
  "relatedTicketIds": [],
  "policyCitations": []
}
```

#### Example `curl`

```bash
curl -X POST http://localhost:8081/api/triage/v1/classify \
  -H "Content-Type: application/json" \
  -d '{
    "incomingRequestId": 123,
    "message": "The reschedule button is disabled on my appointment.",
    "ticketId": 912,
    "allowedTicketTypes": [
      {
        "type": "BILLING_REFUND",
        "description": "User is asking for a refund or billing reimbursement"
      },
      {
        "type": "BILLING_OTHER",
        "description": "Other billing-related issue requiring human review"
      },
      {
        "type": "SCHEDULING_CANCELLATION",
        "description": "User wants to cancel or reschedule an appointment"
      },
      {
        "type": "SCHEDULING_OTHER",
        "description": "Scheduling-related issue that does not clearly fit cancellation or rescheduling"
      },
      {
        "type": "ACCOUNT_ACCESS",
        "description": "Problems logging in, password reset, or account access"
      },
      {
        "type": "SUPPORT_OTHER",
        "description": "General support question not clearly related to billing or scheduling"
      },
      {
        "type": "BUG_APP",
        "description": "Bug or error in the user-facing application or UI"
      },
      {
        "type": "BUG_BACKEND",
        "description": "Bug or error in backend systems, APIs, or data processing"
      },
      {
        "type": "ENGINEERING_OTHER",
        "description": "Engineering-related issue that does not clearly fit a known bug category"
      },
      {
        "type": "OTHER",
        "description": "AI cannot confidently classify this request and a human dispatcher must decide"
      }
    ]
  }'
```

---

## Similarity Tickets Service (external dependency)

This service finds historically similar tickets. It is called by the AI triage service; callers normally do **not** need to call it directly, but the contract is documented here for completeness.

- **Base URL**: `http://localhost:8082`
- **Configured by**: `ai-triage.similarity.base-url`

### Search Similar Tickets

- **Method**: `POST`
- **URL**: `http://localhost:8082/api/similarity/tickets/search`
- **Content-Type**: `application/json`

#### Request Body

```json
{
  "ticketType": "BUG_APP",
  "text": "The reschedule button is disabled on my appointment details screen.",
  "maxResults": 5,
  "ticketId": 912
}
```

#### Success Response

```json
{
  "relatedTicketIds": [847, 912, 733, 655]
}
```

If the service is unavailable or returns an error, the triage service:

- Logs the error
- Returns an empty `relatedTicketIds` array
- Does **not** fail the main triage request

#### Example `curl`

```bash
curl -X POST http://localhost:8082/api/similarity/tickets/search \
  -H "Content-Type: application/json" \
  -d '{
    "ticketType": "BUG_APP",
    "text": "The reschedule button is disabled on my appointment details screen.",
    "maxResults": 5,
    "ticketId": 912
  }'
```

---

## Company Policy / Document Search Service (external dependency)

This service searches company policy and documentation and returns relevant excerpts with RBAC team permissions. It is called by the AI triage service to populate `policyCitations`.

- **Base URL**: `http://localhost:8084`
- **Configured by**: `ai-triage.documents.base-url`

### Search Documents

- **Method**: `POST`
- **URL**: `http://localhost:8084/api/documents/search`
- **Content-Type**: `application/json`

#### Request Body

```json
{
  "originalText": "reschedule button disabled",
  "maxResults": 5,
  "minScore": 0.3
}
```

#### Success Response

```json
{
  "results": [
    {
      "documentName": "Known_Bugs_Limitations.txt",
      "documentLink": "/documents/Known_Bugs_Limitations.txt",
      "citation": "BUG-001\nTitle: Reschedule Button Disabled on Existing Appointments\n\nSymptoms:\nUsers report that the \"Reschedule\" button is disabled on the appointment details page, even though the appointment is more than 24 hours in the future.",
      "score": 0.8257503518005138,
      "rbacTeams": ["dispatch", "engineering"]
    },
    {
      "documentName": "Billing_Refund_Policy.txt",
      "documentLink": "/documents/Billing_Refund_Policy.txt",
      "citation": "The cancel or reschedule button was unavailable before the cancellation window closed...",
      "score": 0.7052839760855728,
      "rbacTeams": ["billing", "dispatch"]
    }
  ]
}
```

#### Example `curl`

```bash
curl -X POST http://localhost:8084/api/documents/search \
  -H "Content-Type: application/json" \
  -d '{
    "originalText": "reschedule button disabled",
    "maxResults": 5,
    "minScore": 0.3
  }'
```

---

## Response Field Reference (AI Triage)

- **status**: `"OK"` or `"FAILED"`
- **ticketType**: Selected ticket type (must be one of the provided `allowedTicketTypes.type`)
- **urgencyScore**: Integer between 1 and 10
- **aiConfidencePercent**: Integer between 0 and 100
- **relatedTicketIds**: Array of historical ticket IDs returned by the similarity service
- **policyCitations**: Array of objects derived from the document search service:
  - **documentName**: Name of the document
  - **documentLink**: Link/path to the document
  - **citation**: Relevant excerpt text
  - **score**: Relevance score (0.0–1.0)
  - **rbacTeams**: Array of RBAC team names that are allowed to access the document
- **failReason**: Present only when `status` is `"FAILED"`, contains a machine-readable reason string

