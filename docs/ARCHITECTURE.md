# MediFlow: AI-Assisted Helpdesk Demo Architecture

This repository documents the architecture of the **MediFlow AI-assisted helpdesk demo**.  
The system demonstrates how user support requests flow from a user-facing app through AI triage, similarity search, and internal knowledge retrieval into a centralized helpdesk.

---

## Table of Contents
- [System Overview](#system-overview)
- [Quick Start (Demo)](#quick-start-demo)
- [Repo: MediFlow User-Facing App](#repo-mediflow-user-facing-app)
- [Repo: MediFlow Helpdesk](#repo-mediflow-helpdesk)
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

1. [mediflow-user-facing](../services/mediflow-user-facing/)  
   ```bash
   mvn quarkus:dev
   ```

2. [mediflow-helpdesk](../services/mediflow-helpdesk/)  
   ```bash
   docker-compose up -d
   ./mvnw quarkus:dev -DDemoData=true
   # or:
   # ./mvnw quarkus:dev -DKeepData=true
   ```

3. [mediflow-ai-triage](../services/mediflow-ai-triage/)  
   ```bash
   mvn quarkus:dev
   ```

4. [Repo: Ticket Similarity Service](https://github.com/LizeRaes/mediflow-similar-tickets)  
   ```bash
   docker-compose up -d
   mvn quarkus:dev
   ```

5. [Repo: Company Documents RAG Service](https://github.com/LizeRaes/mediflow-company-rag)  
   ```bash
   docker-compose up -d
   mvn quarkus:dev -DDemoData=true
   ```
---

<a id="repo-mediflow-user-facing-app"></a>
## Repo: MediFlow User-Facing App
Purpose  
Simulated medical scheduling application where users book appointments and submit help requests.

Path: [services/mediflow-user-facing](../services/mediflow-user-facing/)

UI : http://localhost:8083

Startup  
`mvn quarkus:dev`

---

<a id="repo-mediflow-helpdesk"></a>
## Repo: MediFlow Helpdesk

Purpose  
System of record for all tickets. Handles dispatching, lifecycle management, AI-created tickets, and RBAC-controlled document visibility.

Path: [services/mediflow-helpdesk](../services/mediflow-helpdesk/)

UI : http://localhost:8080

Startup  
`docker-compose up -d`  
`./mvnw quarkus:dev -DDemoData=true` (or `./mvnw quarkus:dev -DKeepData=true`)

---

<a id="repo-ai-triage-service"></a>
## Repo: AI Triage Service

Purpose  
Classifies incoming requests using an LLM, assigns urgency, finds related tickets, and retrieves policy citations.

Path: [services/mediflow-ai-triage](../services/mediflow-ai-triage/)

UI : http://localhost:8081

Startup  
`mvn quarkus:dev`

---

<a id="repo-ticket-similarity-service"></a>
## Repo: Ticket Similarity Service

Purpose  
Stores ticket embeddings and returns similar historical tickets using vector search.

Repo: [Ticket Similarity Service](https://github.com/LizeRaes/mediflow-similar-tickets)

UI: http://localhost:8082

Startup  
```bash
docker-compose up -d
mvn quarkus:dev
```

---

<a id="repo-company-documents-rag-service"></a>
## Repo: Company Documents RAG Service

Purpose  
Stores internal company documents and returns relevant policy citations with RBAC controls.

Repo: [Company Documents RAG Service](https://github.com/LizeRaes/mediflow-company-rag)

UI: http://localhost:8084

Startup  
```bash
docker-compose up -d
mvn quarkus:dev -DDemoData=true
```
