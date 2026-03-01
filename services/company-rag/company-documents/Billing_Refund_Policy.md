# Billing Refund Policy

## Scope

This policy governs refunds for appointment-related charges processed through MedicalAppointment. It applies to all billing-related support requests.

## Definitions

| Term | Meaning |
|---|---|
| Appointment Time | Scheduled start time of the appointment |
| Cancellation Window | Period ending 24 hours before Appointment Time |
| System Error | Confirmed application behavior that blocks a supported user action |
| Duplicate Charge | More than one charge for the same appointment without user intent |

## Eligibility Rules

A refund may be approved only when all required conditions are true.

| Condition | Required |
|---|---|
| User attempted a supported action within cancellation window | Yes |
| Attempt failed due to a confirmed system error | Yes |
| Appointment did not take place | Yes |

### Common Eligible Scenarios

- Cancel/reschedule action unavailable before window closed
- Duplicate appointment charge for one booking
- Confirmed defect created incorrect billing status

## Non-Refundable Scenarios

| Scenario | Refund Allowed |
|---|---|
| User missed the appointment | No |
| Cancellation requested under 24 hours before appointment | No |
| User changed mind after booking | No |
| Appointment completed successfully | No |
| No system error can be confirmed | No |

## Agent Handling Rules

### Agents May

- Acknowledge refund requests
- Explain policy criteria
- Collect appointment ID, date, and issue details

### Agents Must Not

- Promise a refund
- Approve or deny refunds directly
- Imply refunds are automatic
- Bypass internal review

## Decision Authority

Refund approval requires internal review by the Billing Team.

### Billing Reason Codes

| Reason Code | Description |
|---|---|
| BR-100 | Confirmed duplicate charge |
| BR-110 | Supported action blocked by system error |
| BR-120 | Incorrect billing status caused by defect |
| BR-900 | Not eligible under policy |

All approved refunds must be logged with a reason code.

## User Communication Standards

- Use approved response templates only
- Avoid speculative language
- Do not disclose internal billing mechanisms

