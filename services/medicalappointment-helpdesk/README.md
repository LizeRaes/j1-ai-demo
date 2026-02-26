# MedicalAppointment Ticketing System

A system-of-record ticketing application for **MedicalAppointment** (a digital healthcare logistics application).

**Note**: This is the **ticketing/support system** used by MedicalAppointment's customer service and engineering teams
to handle user complaints, questions, and bug reports. MedicalAppointment itself (the user-facing application) runs
separately as another service in this monorepo.

> For the full system architecture and how this service fits in, see the [root README](../../README.md)
> and [docs/](../../docs/).

## Overview

This ticketing system receives complaints, questions, and bug reports from MedicalAppointment users. Customer service
representatives and engineers use this system to:

- View incoming user complaints and questions
- Dispatch tickets to appropriate teams (Scheduling, Billing, Technical Support, etc.)
- Track ticket lifecycle from creation to resolution
- Manage tickets created by AI systems (with accept/reject workflow)
- Monitor all system events in real-time

## Features

- **Three Ticket Creation Paths**:
    - **Incoming complaint → AI Triage → Ticket**: User complaints from MedicalAppointment arrive as incoming requests,
      which are immediately processed by AI triage service (async, non-blocking) to create tickets. The AI triage
      service classifies requests and returns ticket type, urgency score, confidence, related tickets, and policy
      citations. If AI can't classify or triage fails/times out, a ticket with type `OTHER` is created and assigned to
      the DISPATCH team for human review.
    - **Manual ticket creation**: Customer service can create tickets directly (admin/demo)
    - **JSON endpoint ticket creation**: AI systems or external scripts can create tickets via API (for AI/demo scripts)

- **Ticket Type to Team Mapping**:
    - Ticket types automatically determine assigned teams (lowercase team names):
        - `BILLING_*` → `billing` (default user: `billing-user1`)
        - `SCHEDULING_*` → `reschedule` (default user: `reschedule-user1`)
        - `BUG_*` / `ENGINEERING_*` → `engineering` (default user: `engineering-user1`)
        - `ACCOUNT_*` / `SUPPORT_OTHER` / `OTHER` → `dispatch` (default user: `dispatch-user1`)
    - Generic `OTHER` type maps to `dispatch` team (used when AI can't classify a request or triage fails)

- **AI Triage Integration**:
    - External AI triage service URL: `ai-triage.url` (default: `http://localhost:8081`)
    - Async HTTP calls with 3-minute timeout
    - Tickets include related ticket IDs and policy citations with document links from AI analysis
    - Policy citations include document names, links, relevance scores, and RBAC team access controls
    - All triage processing happens in background threads - never blocks request handling

- **Document Access Control (RBAC)**:
    - Documents are only visible to users whose team has access (based on `rbacTeams` in policy citations)
    - Four teams: `dispatch`, `billing`, `reschedule`, `engineering`
    - Document links are hidden if user's team is not in the document's `rbacTeams` list

- **Ticket Lifecycle Management**:
    - Accept tickets
    - Update status (IN_PROGRESS, WAITING_ON_USER, COMPLETED)
    - Reject & Return to Dispatch (for AI tickets)
    - Add comments

- **Live Event Logs**:
    - Real-time event stream in side pane
    - Color-coded events by type and severity
    - Click events to navigate to related tickets

## Technology Stack

- **Backend**: Quarkus 3.30.8, Hibernate ORM + Panache, RESTEasy Reactive
- **Database**: MySQL 8 (Oracle-migratable design)
- **Frontend**: Vanilla HTML/CSS/JavaScript

## Setup

### Prerequisites

- Java 25+
- Maven
- Docker (for MySQL)

### Running the Application

1. **Start MySQL**:
   ```bash
   docker-compose up -d
   ```

2. **Run Quarkus** (choose one of the following modes):

   **Load Demo Data** (clears database and loads tickets from `demo-tickets.json`):
   ```bash
   mvn quarkus:dev -DDemoData=true
   ```

   **Start with Empty Database**:
   ```bash
   mvn quarkus:dev -DEmpty=true
   ```

   **Keep Existing Data** (default):
   ```bash
   mvn quarkus:dev
   # or explicitly:
   mvn quarkus:dev -DKeepData=true
   ```

3. **Access the Application**:
    - Open http://localhost:8080

## API Endpoints

### Incoming Requests

- `POST /api/incoming-requests` - Create incoming request (automatically processed by AI triage)
- `POST /api/intake/incoming-request` - Create incoming request via intake form
- `GET /api/incoming-requests?status=NEW` - List incoming requests
- `GET /api/incoming-requests/{id}` - Get incoming request

### Dispatch

- `POST /api/dispatch/submit-ticket` - Submit ticket from dispatch

### Tickets

- `POST /api/tickets/manual` - Create manual ticket
- `POST /api/tickets/from-ai` - Create AI ticket
- `GET /api/tickets?view=inbox|team|mine&team=...&user=...` - List tickets
- `GET /api/tickets/{id}` - Get ticket
- `POST /api/tickets/{id}/accept` - Accept ticket
- `POST /api/tickets/{id}/reject-and-return-to-dispatch` - Reject AI ticket
- `POST /api/tickets/{id}/status` - Update ticket status
- `POST /api/tickets/{id}/comments` - Add comment

### Events

- `GET /api/events/recent?since=timestamp&limit=200` - Get recent events

### Triage Worker (Manual Trigger)

- `POST /api/triage-worker/process` - Manually trigger triage worker to process all NEW requests (normally not needed -
  requests are processed immediately when created)

### Documents (External Service)

- `GET http://localhost:8084/api/documents/content/{documentName}` - Fetch document content by name (e.g.,
  `Known_Bugs_Limitations.txt`)
    - Returns: Plain text document content
    - Example: `curl http://localhost:8084/api/documents/content/Approved_Response_Templates.txt`

## AI Triage Response Format

The AI triage service returns the following response format:

**Success Response:**

```json
{
  "status": "OK",
  "ticketType": "BUG_APP",
  "urgencyScore": 5,
  "aiConfidencePercent": 85,
  "relatedTicketIds": [
    912,
    847
  ],
  "policyCitations": [
    {
      "documentName": "Known_Bugs_Limitations.txt",
      "documentLink": "/documents/Known_Bugs_Limitations.txt",
      "citation": "BUG-001\nTitle: Reschedule Button Disabled on Existing Appointments\n\nSymptoms:\nUsers report that the \"Reschedule\" button is disabled...",
      "score": 0.8257503518005138,
      "rbacTeams": [
        "dispatch",
        "engineering"
      ]
    }
  ]
}
```

**Failed Response:**

```json
{
  "status": "FAILED",
  "failReason": "Timeout or error message"
}
```

### Policy Citations

Each policy citation includes:

- `documentName` (string): Name of the document file
- `documentLink` (string): Path to the document (used for fetching content)
- `citation` (string): Relevant excerpt from the document
- `score` (number): Relevance score (0.0 to 1.0)
- `rbacTeams` (array): List of teams that have access to this document (lowercase: `dispatch`, `billing`, `reschedule`,
  `engineering`)

**RBAC Access Control:**

- Document links are only displayed if the current user's team is in the `rbacTeams` array
- If the user doesn't have access, only the document name is shown (not as a clickable link)
- Clicking a document link fetches the full document content from the documents API and displays it in a modal

## Demo Data

The application supports three startup modes for managing demo data:

- **`-DDemoData=true`**: Clears the database and loads tickets from `src/main/resources/demo-data/demo-tickets.json`.
  Use this to start with a clean set of demo tickets.
- **`-DEmpty=true`**: Clears the database and starts with no data.
- **`-DKeepData=true`** (or no flag): Keeps existing database data. If the database is empty, seeds 5 default incoming
  requests.

### Demo Data File Format

Demo tickets are stored in JSON format at `src/main/resources/demo-data/demo-tickets.json`. Each ticket object supports
the following fields:

**Required fields:**

- `userId` (string): User ID who created the ticket
- `originalRequest` (string): The original complaint/request text
- `ticketType` (string): One of the `TicketType` enum values (e.g., `BILLING_REFUND`, `SCHEDULING_CANCELLATION`,
  `BUG_APP`, etc.)

**Optional fields:**

- `status` (string): Ticket status (defaults to `FROM_DISPATCH`). Valid values: `FROM_DISPATCH`, `FROM_AI`, `TRIAGED`,
  `IN_PROGRESS`, `WAITING_ON_USER`, `COMPLETED`, `RETURNED_TO_DISPATCH`, `ROLLED_BACK`
- `source` (string): Ticket source (defaults to `MANUAL`). Valid values: `MANUAL`, `AI`, `API`
- `assignedTo` (string): User ID to assign ticket to (defaults to team's default user)
- `urgencyFlag` (boolean): Whether ticket is marked urgent (defaults to `false`)
- `urgencyScore` (number): Urgency score 0-10 (optional)
- `aiConfidence` (number): AI confidence score 0-1 (for AI tickets)
- `aiPayloadJson` (string): JSON string with AI payload data (for AI tickets)
- `comments` (array): Array of comment objects, each with:
    - `authorId` (string): User ID of comment author
    - `body` (string): Comment text

**Note**: The `assignedTeam` field is automatically derived from `ticketType` and cannot be set manually in the demo
data file. Teams are stored in lowercase: `dispatch`, `billing`, `reschedule`, `engineering`.

See `src/main/resources/demo-data/demo-tickets-*.json` for example ticket entries (split across multiple files by
category).

## Project Structure

```
src/main/java/com/medicalappointment/ticketing/
  ├── domain/
  │   ├── model/          # JPA entities
  │   └── enums/          # Enum types
  ├── persistence/        # Panache repositories
  ├── service/            # Business logic
  ├── api/                # REST endpoints
  ├── dto/                # Data transfer objects
  └── mapper/             # Entity-DTO mappers

src/main/resources/
  └── META-INF/resources/ # Static web files
      ├── index.html
      ├── css/
      └── js/
```
