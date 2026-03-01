# Security Escalation Policy

## Purpose

Define how support handles possible security incidents. Security tickets are high priority and confidential by default.

## What Counts as a Security Incident

- User reports access to another user account, appointments, or personal data
- Compromised credentials or suspected account takeover
- Unrecognized billing activity
- Any indication of data exposure or misuse

## Severity Matrix

| Severity | Example Trigger | Initial SLA | Owner |
|---|---|---|---|
| SEV-1 Critical | Cross-account data access confirmed | 15 minutes | Security + Engineering |
| SEV-2 High | Account takeover suspected | 30 minutes | Security |
| SEV-3 Medium | Suspicious billing activity with limited evidence | 2 hours | Security + Billing |
| SEV-4 Low | General concern without indicators | 1 business day | Security triage |

## Required Agent Actions

1. Acknowledge report using approved language.
2. Collect minimal facts: account identifier, event time, and observed behavior.
3. Escalate immediately to internal security review.
4. Mark ticket as confidential/high priority.

## Prohibited Agent Actions

- Do not investigate root cause independently.
- Do not confirm impact scope.
- Do not provide technical remediation steps.
- Do not share internal control details or architecture.

## Approved User Messaging Rules

| Rule | Required |
|---|---|
| Acknowledge concern promptly | Yes |
| Confirm investigation is in progress | Yes |
| Promise timeline before triage owner confirms | No |
| Share internal technical details | No |
| Speculate on breach scope | No |

## Escalation Routing

| Incident Type | Routing Target |
|---|---|
| Account takeover or credential misuse | Security team |
| Data exposure indicators | Security + Engineering |
| Billing anomaly with fraud risk | Security + Billing |

