# API Examples for Ticket Creation

## 1. Create Manual Ticket

**Endpoint:** `POST /api/tickets/manual`

```bash
curl -X POST http://localhost:8080/api/tickets/manual \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Role: DISPATCHER" \
  -H "X-Actor-Team: DISPATCHER" \
  -d '{
    "userId": "u-john",
    "originalRequest": "I cannot cancel my appointment through the app.",
    "ticketType": "SCHEDULING",
    "assignedTeam": "SCHEDULING",
    "urgencyFlag": false,
    "urgencyScore": 5
  }'
```

**With all fields:**
```bash
curl -X POST http://localhost:8080/api/tickets/manual \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: admin-user1" \
  -H "X-Actor-Role: ADMIN" \
  -H "X-Actor-Team: ADMIN" \
  -d '{
    "userId": "u-jane",
    "originalRequest": "Payment failed when trying to book appointment.",
    "ticketType": "BILLING",
    "status": "FROM_DISPATCH",
    "assignedTeam": "BILLING",
    "assignedTo": "billing-user1",
    "urgencyFlag": true,
    "urgencyScore": 8
  }'
```

## 2. Create AI-Generated Ticket

**Endpoint:** `POST /api/tickets/from-ai`

```bash
curl -X POST http://localhost:8080/api/tickets/from-ai \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: engineering-user1" \
  -H "X-Actor-Role: ENGINEER" \
  -H "X-Actor-Team: TECHNICAL_SUPPORT" \
  -d '{
    "userId": "u-alice",
    "originalRequest": "The app crashes when I try to upload my insurance card.",
    "ticketType": "UI_BUG",
    "assignedTeam": "TECHNICAL_SUPPORT",
    "urgencyFlag": false,
    "urgencyScore": 6,
    "aiConfidence": 0.85
  }'
```

**With AI payload:**
```bash
curl -X POST http://localhost:8080/api/tickets/from-ai \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: engineering-user1" \
  -H "X-Actor-Role: ENGINEER" \
  -H "X-Actor-Team: TECHNICAL_SUPPORT" \
  -d '{
    "userId": "u-bob",
    "originalRequest": "Error message: E102 when uploading documents.",
    "ticketType": "BACKEND_BUG",
    "assignedTeam": "TECHNICAL_SUPPORT",
    "urgencyFlag": true,
    "urgencyScore": 9,
    "aiConfidence": 0.92,
    "aiPayloadJson": "{\"error_code\":\"E102\",\"category\":\"upload\",\"confidence\":0.92}"
  }'
```

## 3. Dispatch Ticket from Incoming Request

**Endpoint:** `POST /api/dispatch/submit-ticket`

**Note:** Requires an existing incoming request ID. First create one:

```bash
# First, create an incoming request
curl -X POST http://localhost:8080/api/incoming-requests \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "u-charlie",
    "channel": "web",
    "rawText": "I need to reschedule my appointment for next week."
  }'
```

**Then dispatch it:**
```bash
curl -X POST http://localhost:8080/api/dispatch/submit-ticket \
  -H "Content-Type: application/json" \
  -H "X-Actor-Id: dispatch-user1" \
  -H "X-Actor-Role: DISPATCHER" \
  -H "X-Actor-Team: DISPATCHER" \
  -d '{
    "incomingRequestId": 1,
    "ticketType": "SCHEDULING",
    "assignedTeam": "SCHEDULING",
    "urgencyFlag": false,
    "urgencyScore": 4,
    "dispatcherId": "dispatch-user1",
    "notes": "User wants to reschedule appointment"
  }'
```

## Available Ticket Types

- `SCHEDULING`
- `BILLING`
- `UI_BUG`
- `BACKEND_BUG`
- `POLICY_QUESTION`
- `URGENT_CANCELLATION`
- `OTHER`

## Available Teams

- `DISPATCHER`
- `BILLING`
- `SCHEDULING`
- `TECHNICAL_SUPPORT`
- `CUSTOMER_SERVICE`
- `ADMIN`

## Available Ticket Statuses (for manual tickets)

- `FROM_DISPATCH`
- `FROM_AI`
- `TRIAGED`
- `IN_PROGRESS`
- `WAITING_ON_USER`
- `COMPLETED`
- `RETURNED_TO_DISPATCH`

## Notes

- **Actor Headers**: All endpoints accept `X-Actor-Id`, `X-Actor-Role`, and `X-Actor-Team` headers for demo mode
- **Auto-assignment**: When a ticket is dispatched to a team, it's automatically assigned to the default user for that team:
  - DISPATCHER → dispatch-user1
  - BILLING → billing-user1
  - SCHEDULING → scheduling-user1
  - TECHNICAL_SUPPORT → engineering-user1
  - CUSTOMER_SERVICE → customer-service-user1
  - ADMIN → admin-user1
- **Urgency Score**: Range is 1-10 (not 0-1)
- **AI Confidence**: Range is 0.0-1.0
