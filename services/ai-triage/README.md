# AI Triage Service

A Quarkus application that uses AI to automatically classify and triage customer support tickets for MedicalAppointment, a medical appointment scheduling application. The service classifies incoming requests, assigns urgency scores, and finds similar historical tickets.

## Features

- **AI-Powered Classification**: Uses OpenAI GPT-4o-mini to classify tickets into predefined categories
- **Urgency Scoring**: Assigns urgency scores (1-10) based on request criticality
- **Confidence Metrics**: Provides AI confidence scores (0-100) for classification decisions
- **Similar Ticket Discovery**: Integrates with similarity service to find related historical tickets
- **Policy Document Search**: Integrates with document service to find relevant company policy documents and citations
- **Resilient Error Handling**: Continues operation even if similarity or document services are unavailable

## Prerequisites

- **Java 25**
- **Maven 3.8+**
- **OpenAI API Key** (set as `OPENAI_API_KEY` environment variable)
- **Similarity Tickets Service** (optional, runs on port 8082 by default)
- **Document Search Service** (optional, runs on port 8084 by default)

## Setup

1. **Set OpenAI API Key:**
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```

2. **Build and run:**
   ```bash
   mvn quarkus:dev
   ```

The application will run on port **8081**.

### Running with Local / OpenAI-Compatible Models

The service can run against any OpenAI-compatible API by changing the base URL and model name.

#### Docker Model Runner (Local Model)

**Enable in Docker Desktop** → Settings → AI:
- Enable Docker Model Runner
- Enable host-side TCP support

**Pull a model**
```bash
docker model pull ai/llama3.2:1B-Q8_0
```

**Configure** `application.properties`
```properties
quarkus.langchain4j.openai.base-url=http://localhost:12434/engines/llama.cpp/v1
quarkus.langchain4j.openai.chat-model.model-name=ai/llama3.2:1B-Q8_0
```

**Verify**
```bash
curl http://localhost:12434/engines/llama.cpp/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer not-needed" \
  -d '{"model":"ai/llama3.2:1B-Q8_0","stream":false,"messages":[{"role":"user","content":"Hi"}]}'
```

#### NVIDIA NIM

```properties
quarkus.langchain4j.openai.base-url=https://integrate.api.nvidia.com/v1
quarkus.langchain4j.openai.api-key=${NIM_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=meta/llama-3.1-8b-instruct
```

**Verify**
```bash
curl https://integrate.api.nvidia.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $NIM_API_KEY" \
  -d '{"model":"meta/llama-3.1-8b-instruct","stream":false,"messages":[{"role":"user","content":"Hi"}]}'
```

## Configuration

Edit `src/main/resources/application.properties` to configure:

```properties
# Server port
quarkus.http.port=8081

# AI Triage Model Configuration
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY}
quarkus.langchain4j.openai.chat-model.model-name=gpt-4o-mini
quarkus.langchain4j.openai.chat-model.temperature=0.2
quarkus.langchain4j.openai.chat-model.timeout=15s

# Similarity Service Configuration
quarkus.rest-client.similarity.url=http://localhost:8082
ai-triage.similarity.max-results=5
ai-triage.similarity.min-score=0.3

# Document Service Configuration
ai-triage.documents.max-results=5
ai-triage.documents.min-score=0.3
quarkus.rest-client.company-rag-documents.url=http://localhost:8084

# helpdesk
ai-triage.helpdesk.base-url=http://localhost:8080

# UI Configuration
ai-triage.ui.default-zoom-percent=100
ai-triage.ui.polling.enabled=true
ai-triage.ui.polling.interval-ms=2000
```

## API Endpoint

### Classify Ticket

**POST** `/api/triage/v1/classify`

Classifies a customer request and returns ticket type, urgency score, confidence, related ticket IDs, and policy document citations with RBAC team permissions.

**Request:**
```json
{
  "incomingRequestId": 123,
  "message": "The reschedule button is disabled on my appointment.",
  "ticketId": 912,
  "allowedTicketTypes": [
    {
      "type": "BUG_APP",
      "description": "Bug or error in the user-facing application or UI"
    },
    {
      "type": "SCHEDULING_OTHER",
      "description": "Scheduling-related issue that does not clearly fit cancellation or rescheduling"
    }
  ]
}
```

**Success Response:**
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
      "citation": "BUG-001\nTitle: Reschedule Button Disabled on Existing Appointments\n\nSymptoms:\nUsers report that the \"Reschedule\" button is disabled...",
      "score": 0.8257503518005138,
      "rbacTeams": ["dispatch", "engineering"]
    }
  ]
}
```

**Error Response:**
```json
{
  "status": "FAILED",
  "failReason": "AI_TRIAGE_FAILED: PARSE_FAILED: ...",
  "relatedTicketIds": [],
  "policyCitations": []
}
```

### Policy Citation Structure

Each policy citation in the response includes:
- **documentName**: Name of the policy document
- **documentLink**: Link to the full document
- **citation**: Relevant excerpt from the document
- **score**: Relevance score (0.0 to 1.0)
- **rbacTeams**: List of RBAC teams that have permission to access this document (e.g., `["dispatch", "engineering"]`, `["billing", "dispatch"]`)

## Testing with cURL

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

## Direct Document Search Endpoint

The document search service can also be called directly to search for policy documents:

**POST** `http://localhost:8084/api/documents/search`

**Request:**
```json
{
  "originalText": "reschedule button disabled",
  "maxResults": 5,
  "minScore": 0.3
}
```

**Response:**
```json
{
  "results": [
    {
      "documentName": "Known_Bugs_Limitations.txt",
      "documentLink": "/documents/Known_Bugs_Limitations.txt",
      "citation": "BUG-001\nTitle: Reschedule Button Disabled on Existing Appointments\n\nSymptoms:\nUsers report that the \"Reschedule\" button is disabled...",
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

**Example cURL:**
```bash
curl -X POST http://localhost:8084/api/documents/search \
  -H "Content-Type: application/json" \
  -d '{
    "originalText": "reschedule button disabled",
    "maxResults": 5,
    "minScore": 0.3
  }'
```

## How It Works

1. **Request Validation**: Validates that message, ticketId, and allowed ticket types are provided
2. **AI Classification**: Calls OpenAI GPT-4o-mini with system prompt describing MedicalAppointment and classification rules
3. **Result Validation**: Ensures AI response matches allowed types and value constraints
4. **Similarity Search**: Queries similarity service to find related historical tickets (non-blocking), passing the ticketId
5. **Document Search**: Queries document service to find relevant policy/company documents (non-blocking) based on the message text
6. **Response Assembly**: Returns classification with urgency, confidence, related ticket IDs, and policy citations with RBAC team permissions

## Error Handling

- **Similarity Service Errors**: Logged as errors but don't fail the request; returns empty `relatedTicketIds`
- **Document Service Errors**: Logged as errors but don't fail the request; returns empty `policyCitations`
- **AI Service Errors**: Returned to caller with detailed error information
- **Combined Errors**: If multiple services fail, errors are concatenated in the response

## Tech Stack

- **Quarkus 3.30.8** - Java framework
- **LangChain4j 0.35.0** - AI integration library
- **OpenAI GPT-4o-mini** - LLM for ticket classification
- **Jackson** - JSON serialization
- **Java HTTP Client** - Similarity service integration
