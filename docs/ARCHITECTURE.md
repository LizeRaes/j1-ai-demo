# MedicalAppointment: AI-Assisted Helpdesk Demo Architecture

This repository documents the architecture of the **MedicalAppointment AI-assisted helpdesk demo**.  
The system demonstrates how user support requests flow from a user-facing app through AI triage, similarity search, and internal knowledge retrieval into a centralized helpdesk.

---

## Table of Contents
- [System Overview](#system-overview)
- [Quick Start (Demo)](#quick-start-demo)
- [Repo: MedicalAppointment User-Facing App](#repo-medicalappointment-user-facing-app)
- [Repo: MedicalAppointment Helpdesk](#repo-medicalappointment-helpdesk)
- [Repo: AI Triage Service](#repo-ai-triage-service)
- [Repo: Ticket Similarity Service](#repo-ticket-similarity-service)
- [Repo: Company Documents RAG Service](#repo-company-documents-rag-service)

---

## System Overview

<img src="./system_components.png" alt="System Components" width="50%">

![Sequence Diagram](./sequence_diagram.png)

---

## Quick Start (Demo)

### Required Environment Variables
All AI-powered services require an OpenAI API key.  
**Set the `OPENAI_API_KEY` in your environment variables.**

### Start Order

1. [medicalappointment-user-facing](../services/medicalappointment-user-facing/)  
   ```bash
   mvn quarkus:dev
   ```

2. [medicalappointment-helpdesk](../services/medicalappointment-helpdesk/)  
   ```bash
   docker-compose up -d
   mvn quarkus:dev -DDemoData=true
   # or:
   # mvn quarkus:dev -DKeepData=true
   ```

3. [medicalappointment-ai-triage](../services/medicalappointment-ai-triage/)  
   ```bash
   mvn quarkus:dev
   ```

4. [medicalappointment-similar-tickets](../services/medicalappointment-similar-tickets/)  
   ```bash
   docker-compose up -d
   mvn clean verify && java -jar target/similar-tickets.jar
   ```

5. [medicalappointment-company-rag](../services/medicalappointment-company-rag/)  
   ```bash
   docker-compose up -d
   mvn quarkus:dev -DDemoData=true
   ```
---

<a id="repo-medicalappointment-user-facing-app"></a>
## Repo: MedicalAppointment User-Facing App
Purpose  
Simulated medical scheduling application where users book appointments and submit help requests.

Path: [services/medicalappointment-user-facing](../services/medicalappointment-user-facing/)

UI : http://localhost:8083

Startup  
`mvn quarkus:dev`

---

<a id="repo-medicalappointment-helpdesk"></a>
## Repo: MedicalAppointment Helpdesk

Purpose  
System of record for all tickets. Handles dispatching, lifecycle management, AI-created tickets, and RBAC-controlled document visibility.

Path: [services/medicalappointment-helpdesk](../services/medicalappointment-helpdesk/)

UI : http://localhost:8080

Startup  
`docker-compose up -d`  
`mvn quarkus:dev -DDemoData=true` (or `mvn quarkus:dev -DKeepData=true`)

---

<a id="repo-ai-triage-service"></a>
## Repo: AI Triage Service

Purpose  
Classifies incoming requests using an LLM, assigns urgency, finds related tickets, and retrieves policy citations.

Path: [services/medicalappointment-ai-triage](../services/medicalappointment-ai-triage/)

UI : http://localhost:8081

Startup  
`mvn quarkus:dev`

---

<a id="repo-ticket-similarity-service"></a>
## Repo: Ticket Similarity Service

Purpose  
Stores ticket embeddings and returns similar historical tickets using vector search.

Path: [services/medicalappointment-similar-tickets](../services/medicalappointment-similar-tickets/)

UI: http://localhost:8082

Startup  
```bash
docker-compose up -d
mvn clean verify && java -jar target/similar-tickets.jar
```

---

<a id="repo-company-documents-rag-service"></a>
## Repo: Company Documents RAG Service

Purpose  
Stores internal company documents and returns relevant policy citations with RBAC controls.

Path: [services/medicalappointment-company-rag](../services/medicalappointment-company-rag/)

UI: http://localhost:8084

Startup  
```bash
docker-compose up -d
mvn quarkus:dev -DDemoData=true
```
