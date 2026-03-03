# MedicalAppointment API Contracts

Base URL (local dev): `http://localhost:8083`

---

## 1. Landing Page

### `GET /`

- **Description**: Main landing page with navigation links.
- **Request Body**: _None_
- **Response**: HTML (MedicalAppointment home page)

**cURL**
```bash
curl -i http://localhost:8083/
```

**Example Response (shortened)**
```http
HTTP/1.1 200 OK
Content-Type: text/html; charset=UTF-8

<!DOCTYPE html>
<html>
  <head>...</head>
  <body>
    <h1>🏥 MedicalAppointment</h1>
    <!-- navigation links -->
  </body>
</html>
```

---

## 2. Appointments

### 2.1 `GET /appointments/book`

- **Description**: Returns the appointment booking form.
- **Request Body**: _None_
- **Response**: HTML form.

**cURL**
```bash
curl -i http://localhost:8083/appointments/book
```

---

### 2.2 `POST /appointments/book`

- **Description**: Creates a new appointment for the default user (`u-charlie`) and redirects to payment.
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body (form)**:
  - `doctor` (e.g. `Dr. Alice`)
  - `date` (`today` | `tomorrow` | `next week`)
  - `time` (`09:00` | `10:00` | `14:00`)

**cURL**
```bash
curl -i -X POST http://localhost:8083/appointments/book \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "doctor=Dr. Alice&date=today&time=09:00"
```

**Example Response**
```http
HTTP/1.1 303 See Other
Location: /billing/pay?appointmentId=<generated-id>
```

On validation error (missing fields):
```http
HTTP/1.1 400 Bad Request
Content-Type: text/plain;charset=UTF-8

Invalid appointment data
```

---

### 2.3 `GET /appointments`

- **Description**: Lists all appointments for the default user.
- **Response**: HTML page with a table of appointments.

**cURL**
```bash
curl -i http://localhost:8083/appointments
```

---

### 2.4 `POST /appointments/{id}/cancel`

- **Description**: Cancels an existing appointment.
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body**: _None_

**cURL**
```bash
curl -i -X POST http://localhost:8083/appointments/{id}/cancel
```

**Example Response**
```http
HTTP/1.1 303 See Other
Location: /appointments
```

---

### 2.5 `POST /appointments/{id}/reschedule`

- **Description**: Reschedules an existing appointment to a new date/time.
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body (form)**:
  - `date` (`today` | `tomorrow` | `next week`)
  - `time` (`09:00` | `10:00` | `14:00`)

**cURL**
```bash
curl -i -X POST http://localhost:8083/appointments/{id}/reschedule \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "date=next+week&time=10:00"
```

**Example Response**
```http
HTTP/1.1 303 See Other
Location: /appointments
```

---

## 3. Billing

### 3.1 `GET /billing/pay`

- **Description**: Shows the fake payment page.
- **Query Params**:
  - `appointmentId` (optional, links payment to an appointment)

**cURL**
```bash
curl -i "http://localhost:8083/billing/pay?appointmentId=1234"
```

---

### 3.2 `POST /billing/pay`

- **Description**: Marks payment as paid and (if `appointmentId` present) confirms the appointment.
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body (form)**:
  - `appointmentId` (optional)

**cURL**
```bash
curl -i -X POST http://localhost:8083/billing/pay \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "appointmentId=1234"
```

**Example Response**
```http
HTTP/1.1 303 See Other
Location: /billing/pay/success
```

---

### 3.3 `GET /billing/pay/success`

- **Description**: Simple "Payment successful" page.

**cURL**
```bash
curl -i http://localhost:8083/billing/pay/success
```

---

## 4. Account

### 4.1 `GET /account`

- **Description**: Shows fake account details for the default user.
- **Query Params (optional)**:
  - `message` – success message (e.g. after reset)
  - `error` – error message

**cURL**
```bash
curl -i http://localhost:8083/account
```

---

### 4.2 `POST /account/reset-password`

- **Description**: Attempts a password reset (randomly succeeds/fails for demo).
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body**: _None_

**cURL**
```bash
curl -i -X POST http://localhost:8083/account/reset-password
```

**Example Responses**
```http
HTTP/1.1 303 See Other
Location: /account?message=Password+reset+email+sent
```

or

```http
HTTP/1.1 303 See Other
Location: /account?error=Password+reset+failed
```

---

## 5. Help / Support (Web App)

### 5.1 `GET /help`

- **Description**: Help/support page with textarea and submit button.
- **Query Params (optional)**:
  - `submitted` – when present, shows success banner
  - `error` – when present, shows error banner with message

**cURL**
```bash
curl -i http://localhost:8083/help
```

---

### 5.2 `POST /help` (Form)

- **Description**: Submits a help request via HTML form, forwards to external helpdesk.
- **Content-Type**: `application/x-www-form-urlencoded`
- **Request Body (form)**:
  - `message` – free text from user

**cURL**
```bash
curl -i -X POST http://localhost:8083/help \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "message=I+cannot+cancel+my+appointment+through+the+app."
```

**Success Response (redirect)**
```http
HTTP/1.1 303 See Other
Location: /help?submitted=true
```

**Error Response (redirect)**
```http
HTTP/1.1 303 See Other
Location: /help?error=<url-encoded-error-message>
```

---

### 5.3 `POST /help` (JSON)

- **Description**: JSON API version of help submission.
- **Content-Type**: `application/json`
- **Request Body** (`IncomingRequest`):

```json
{
  "userId": "u-charlie",   // optional, defaulted if missing
  "channel": "web",
  "rawText": "I cannot cancel my appointment through the app."
}
```

**cURL**
```bash
curl -i -X POST http://localhost:8083/help \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "u-charlie",
    "channel": "web",
    "rawText": "I cannot cancel my appointment through the app."
  }'
```

**Example Success Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{"status": "submitted"}
```

**Example Error Response**
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{"status": "error", "message": "HTTP 400 - Bad Request (check endpoint and request format)"}
```

---

## 6. External Helpdesk Ingestion API

> This is the **external** system MedicalAppointment sends tickets to. It is not served by this app, but the contract is important.

Base URL (external helpdesk, local dev): `http://localhost:8080`

### `POST /api/intake/incoming-request`

- **Description**: Ingests a new helpdesk ticket.
- **Content-Type**: `application/json`
- **Request Body** (`HelpdeskRequest`):

```json
{
  "userId": "string",
  "message": "string"
}
```

**cURL**
```bash
curl -i -X POST http://localhost:8080/api/intake/incoming-request \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "u-john",
    "message": "I cannot cancel my appointment through the app."
  }'
```

**Example Success Response**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "id": 903,
  "userId": "u-john",
  "channel": "intake-ui",
  "rawText": "I cannot cancel my appointment through the app.",
  "status": "AI_TRIAGE_IN_PROGRESS",
  "createdAt": "2026-01-26T23:54:31.77986+01:00",
  "updatedAt": "2026-01-26T23:54:31.779875+01:00"
}
```

**Example Error Response (generic)**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{"error": "Invalid request"}
```

