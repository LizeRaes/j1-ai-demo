# Known Bugs and Limitations

## Purpose

This document lists confirmed defects that are actively tracked or under investigation. Use it to avoid duplicate escalation and provide accurate guidance.

## Issue Overview

| Bug ID | Short Title | Status | Tracking Ref | ETA |
|---|---|---|---|---|
| BUG-001 | Reschedule button disabled | In progress | ENG-2417 | ~7 days |
| BUG-002 | Cancel appears successful but still visible | Queued | ENG-2399 | No ETA |
| BUG-003 | Payment success page without status update | In fixing | ENG-2403 | ~3 days |
| BUG-004 | Help request submitted multiple times | Planned | ENG-2381 | Next minor UI update |
| BUG-005 | Appointment list empty after restart | Known limitation | ENG-2300 | No fix planned |

## Detailed Entries

### BUG-001 - Reschedule Button Disabled on Existing Appointments

- **Symptoms:** Reschedule button disabled even when appointment is more than 24 hours away.
- **Affected Conditions:** Older sessions, Safari, and some older Firefox versions.
- **Workaround:** Refresh page or use Chromium-based browser. In some cases, cancel/rebook resolves it.
- **Status:** In progress

### BUG-002 - Cancel Action Appears Successful but Appointment Remains Visible

- **Symptoms:** User sees confirmation, but appointment still appears in list.
- **Affected Conditions:** Intermittent after restart, more likely with multiple appointments.
- **Workaround:** Refresh page. If still visible, reassure user that cleanup occurs automatically.
- **Status:** Queued for investigation

### BUG-003 - Payment Confirmation Page Displayed Without Payment Status Update

- **Symptoms:** Success page shown, appointment remains unpaid.
- **Affected Conditions:** Frequent after browser back-navigation during payment flow.
- **Workaround:** Ask user not to retry payment immediately; support verifies duplicate charge risk internally.
- **Status:** In fixing

### BUG-004 - Help Request Submitted Multiple Times

- **Symptoms:** Multiple confirmations after one submit action.
- **Affected Conditions:** Repeated clicks and slower networks.
- **Workaround:** Reassure user duplicate submissions are filtered internally.
- **Status:** Planned

### BUG-005 - Appointment List Empty After Application Restart

- **Symptoms:** Previously booked appointments no longer visible.
- **Affected Conditions:** App restart or redeploy.
- **Workaround:** Inform user issue is known; advise rebooking if urgently needed.
- **Status:** Known limitation

## Agent Guidance

| Situation | Agent Action |
|---|---|
| Symptoms match a known bug | Do not escalate unless materially new symptoms appear |
| Ticket references a known issue | Include internal tracking ID |
| Workaround exists | Provide workaround from this document |
| Symptoms differ materially | Treat as new bug and escalate |

