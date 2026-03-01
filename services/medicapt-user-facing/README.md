# MedicalAppointment - Medical Scheduling System

MedicalAppointment is a web-based medical appointment scheduling system that enables patients to book, manage, and reschedule appointments with healthcare providers. The system provides a user-friendly interface for appointment management and integrates with a centralized helpdesk system to route all user support requests.

## System Overview

MedicalAppointment allows users to:
- Browse available doctors and time slots
- Book new appointments
- View and manage existing appointments
- Process payments for appointments
- Manage account settings
- Submit support requests through the integrated help system

All user support requests and feedback are automatically routed to the centralized helpdesk system for processing and resolution.

## Tech Stack

- **Java 25**
- **Quarkus 3.8.0** (Reactive framework)
- **Maven** (Build tool)
- **Qute** (Server-side templating)
- **RESTEasy Reactive** (REST API framework)

## Helpdesk Integration

MedicalAppointment integrates with the centralized helpdesk system to ensure all user support requests are properly logged and processed. When users submit help requests through the web interface, the system automatically forwards these requests to the helpdesk ingestion endpoint.

For the full API contract details, see [CONTRACTS.md](CONTRACTS.md).

## API Endpoints

### Web Application Endpoints

#### Appointment Management
- `GET /` - Landing page with navigation
- `GET /appointments/book` - Display appointment booking form
- `POST /appointments/book` - Create a new appointment
- `GET /appointments` - List all appointments for the current user
- `POST /appointments/{id}/cancel` - Cancel a specific appointment
- `POST /appointments/{id}/reschedule` - Reschedule a specific appointment

#### Billing
- `GET /billing/pay` - Display payment page
- `POST /billing/pay` - Process payment for an appointment
- `GET /billing/pay/success` - Display payment confirmation

#### Account Management
- `GET /account` - Display user account information
- `POST /account/reset-password` - Request password reset

#### Support
- `GET /help` - Display help/support page
- `POST /help` - Submit help request (form-encoded)
- `POST /help` - Submit help request (JSON, Content-Type: application/json)

### Helpdesk API Endpoint

- `POST /api/incoming-requests` - Receives helpdesk tickets (internal demo endpoint)

Outbound help requests are forwarded to the external helpdesk system. See [CONTRACTS.md](CONTRACTS.md) for details.

## Getting Started

### Prerequisites

- Java 25 or higher
- Maven 3.8 or higher

### Running the Application

**Development Mode** (with hot reload):
```bash
mvn quarkus:dev
```

**Production Mode**:
```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

**Access the application:**
- Web Interface: `http://localhost:8083`
- Helpdesk API (receives tickets from MedicalAppointment): `http://localhost:8080/api/intake/incoming-request`

## Architecture

For the full system architecture, see the [root README](../../README.md).

### Internal Structure

```
┌─────────────────────────────────────┐
│      MedicalAppointment User-Facing App       │
│  (Quarkus)                          │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Web UI Endpoints             │  │
│  │  - Appointment Management     │  │
│  │  - Billing                    │  │
│  │  - Account                    │  │
│  │  - Help/Support               │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Domain Services              │  │
│  │  - SchedulingService          │  │
│  │  - BillingService             │  │
│  │  - AccountService             │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  HelpdeskClient               │  │
│  │  (Forwards to helpdesk :8080) │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

## Important Notes

### Demo Application

**This is a demonstration application with many aspects intentionally not implemented.** The following limitations exist by design:

- **No Database**: All data is stored in-memory and will be lost on application restart
- **No Authentication**: User sessions are not managed; a default user is used for all operations
- **No Payment Processing**: Payment operations are simulated and do not process real transactions
- **No Calendar Integration**: Appointment availability is not validated against real calendars
- **No Email Notifications**: Users do not receive email confirmations or reminders
- **Minimal Validation**: Input validation is intentionally minimal for demonstration purposes
- **No Transaction Management**: Database transactions are not used
- **Static Data**: Doctor lists, time slots, and other data are hardcoded

These limitations are intentional and allow the system to:
- Generate realistic helpdesk tickets for demonstration purposes
- Easily introduce and fix bugs for testing scenarios
- Focus on the helpdesk integration workflow
- Provide a simple, understandable codebase

### Default User

For demonstration purposes, all operations use a default user:
- **User ID**: `u-charlie`
- **Email**: `charlie@example.com`

## Development

### Project Structure

```
src/main/java/com/medicalappointment/
├── model/              # Domain models
│   ├── Appointment.java
│   ├── AppointmentStatus.java
│   ├── PaymentStatus.java
│   └── IncomingRequest.java
├── services/           # Business logic
│   ├── SchedulingService.java
│   ├── BillingService.java
│   ├── AccountService.java
│   └── HelpdeskClient.java
└── resources/          # REST endpoints
    ├── HomeResource.java
    ├── AppointmentResource.java
    ├── BillingResource.java
    ├── AccountResource.java
    ├── HelpResource.java
    └── IncomingRequestsResource.java

src/main/resources/
├── application.properties
└── templates/          # Qute HTML templates
    ├── index.html
    ├── bookAppointment.html
    ├── myAppointments.html
    ├── payment.html
    ├── paymentSuccess.html
    ├── account.html
    └── help.html
```

## Support

For questions about the helpdesk integration contract or system architecture, please refer to the helpdesk system documentation or contact the development team.
