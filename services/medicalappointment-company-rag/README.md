# Company Internal Documents RAG

A Quarkus application for storing and searching company internal documents using RAG (Retrieval-Augmented Generation) with LangChain4j and Qdrant.

## Overview

This RAG system allows you to:
- Store and manage company internal documents with smart chunking strategies
- Search documents based on ticket text queries
- Manage document access using RBAC (Role-Based Access Control)
- View all documents and their team permissions in a web dashboard

## Prerequisites

- Java 25
- Maven 3.8+
- Docker (for Qdrant)
- OpenAI API key (for embeddings)

## Setup

1. **Start Qdrant database:**
   ```bash
   docker-compose up -d
   ```

2. **Set your OpenAI API key:**
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```

3. **Build and run the application:**
   ```bash
   mvn quarkus:dev
   ```

The application will run on port **8084**.

**Important:** 
- Make sure Qdrant is running before starting the application, otherwise you'll get connection errors.
- **By default**, the application preserves existing data in the database and does NOT reload documents on startup.
- **To wipe the database and reload all documents** from `src/main/resources/documents/`, use the `DemoData` system property:
  ```bash
  mvn quarkus:dev -DDemoData=true
  ```

## Web Dashboard

The service includes a web dashboard for monitoring documents and activity logs.

**Access the dashboard at:** http://localhost:8084

The dashboard features:
- **Left pane (1/3 width)**: Real-time activity logs showing:
  - Document upsert operations
  - Document search requests
  - Delete operations
  - RBAC updates
- **Right pane (2/3 width)**: Split into two sections:
  - **Top half**: Search results table displaying:
    - Document name (with link)
    - Citation (matching text chunk)
    - Similarity score
    - RBAC teams with access
  - **Bottom half**: All company documents table displaying:
    - Document name
    - RBAC teams with access (or "Company-wide" if no restrictions)

The dashboard automatically refreshes every second to show the latest data.

## Document Chunking

Documents are chunked using intelligent strategies:

- **Default Strategy**: Recursive splitting (300 characters by default, configurable)
- **Approved Response Templates**: Split by "Template: " markers, keeping entire templates as complete chunks
- **Custom Strategies**: Defined in `src/main/resources/config/document-splitting-config.yaml`

### Configuration

Edit `src/main/resources/application.properties` to configure default chunking:
```properties
document.chunking.default.strategy=recursive
document.chunking.default.chunk-size=300
```

## Document Access Policy

Document access is managed via RBAC (Role-Based Access Control) defined in `src/main/resources/config/document_access_policy.yaml`.

- Each document can have a list of teams with read access
- If no teams are specified (empty list), the document is company-wide accessible
- The access policy is automatically loaded on startup and updated when documents are added/modified

## API Endpoints

### 1. Search Documents
**POST** `/api/documents/search`

Searches for similar document chunks based on ticket text.

**Request:**
```json
{
  "originalText": "reschedule button disabled",
  "maxResults": 5,
  "minScore": 0.7
}
```

**Response:**
```json
{
  "results": [
    {
      "documentName": "Approved_Response_Templates.txt",
      "documentLink": "/documents/Approved_Response_Templates.txt",
      "citation": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out...",
      "score": 0.892,
      "rbacTeams": ["dispatch", "billing", "reschedule"]
    }
  ]
}
```

**Fields:**
- `originalText` (required): The ticket text to search for
- `maxResults` (optional): Maximum number of results (default: 5)
- `minScore` (optional): Minimum similarity score threshold (default: 0.0)

### 2. Upsert Document
**POST** `/api/documents/upsert`

Creates or updates a document with its content and RBAC teams.

**Request:**
```json
{
  "documentName": "New_Policy.txt",
  "content": "This is the document content...",
  "rbacTeams": ["billing", "dispatch"]
}
```

**Response:**
```json
{
  "status": "OK"
}
```

**Fields:**
- `documentName` (required): Unique document identifier
- `content` (required): Document content
- `rbacTeams` (optional): List of teams with access. If not specified, document is company-wide.

### 3. Delete Document
**POST** `/api/documents/delete`

Deletes a document and all its embeddings (idempotent).

**Request:**
```json
{
  "documentName": "Old_Policy.txt"
}
```

**Response:**
```json
{
  "status": "OK"
}
```

### 4. Update RBAC
**POST** `/api/documents/rbac/update`

Updates the RBAC teams for an existing document.

**Request:**
```json
{
  "documentName": "Approved_Response_Templates.txt",
  "rbacTeams": ["dispatch", "billing"]
}
```

**Response:**
```json
{
  "status": "OK"
}
```

### 5. Get All Documents
**GET** `/api/documents/all`

Retrieves all stored documents with their RBAC teams.

**Response:**
```json
{
  "documents": [
    {
      "documentName": "Approved_Response_Templates.txt",
      "documentLink": "/documents/Approved_Response_Templates.txt",
      "rbacTeams": ["dispatch", "billing", "reschedule"]
    }
  ]
}
```

### 6. Get Activity Logs
**GET** `/api/documents/logs`

Retrieves activity logs for the dashboard.

**Response:**
```json
{
  "logs": [
    {
      "message": "Document search request: \"reschedule button...\"",
      "type": "search",
      "timestamp": 1706177897000
    }
  ]
}
```

### 7. Get Document Content
**GET** `/api/documents/content/{documentName}`

Retrieves the full content of a document by its name.

**Path Parameters:**
- `documentName` (required): The name of the document file (e.g., "Approved_Response_Templates.txt")

**Response:**
```json
{
  "content": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out..."
}
```

### 8. Get Config
**GET** `/api/documents/config`

Retrieves UI configuration.

**Response:**
```json
{
  "defaultZoom": 100
}
```

## Testing with cURL

Once the service is running on port 8084, you can test it with the following curl commands:

### 1. Search Documents

```bash
curl -X POST http://localhost:8084/api/documents/search \
  -H "Content-Type: application/json" \
  -d '{
    "originalText": "reschedule button disabled",
    "maxResults": 5,
    "minScore": 0.3
  }'
```

### 2. Upsert a Document

```bash
curl -X POST http://localhost:8084/api/documents/upsert \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "Test_Document.txt",
    "content": "This is a test document with some content.",
    "rbacTeams": ["billing", "dispatch"]
  }'
```

### 3. Update RBAC

```bash
curl -X POST http://localhost:8084/api/documents/rbac/update \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "Test_Document.txt",
    "rbacTeams": ["engineering"]
  }'
```

### 4. Delete a Document

```bash
curl -X POST http://localhost:8084/api/documents/delete \
  -H "Content-Type: application/json" \
  -d '{
    "documentName": "Test_Document.txt"
  }'
```

### 5. Get All Documents

```bash
curl http://localhost:8084/api/documents/all
```

### 6. Get Document Content

```bash
curl http://localhost:8084/api/documents/content/Approved_Response_Templates.txt
```

**Expected Response:**
```json
{
  "content": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out..."
}
```

## Data Persistence

- **Embeddings are stored in Qdrant** and persist across application restarts
- **Document metadata** (document name, chunk index) is stored in Qdrant payload
- **Document access policy** is stored in `src/main/resources/config/document_access_policy.yaml`
- Documents are automatically re-embedded on startup from `src/main/resources/documents/`

## Configuration

Edit `src/main/resources/application.properties` to configure:
- Qdrant host and port (default: localhost:6334)
- Collection name (default: document-embeddings)
- OpenAI API key (required for embeddings)
- Default chunking strategy and chunk size

## Technology Stack

- **Quarkus 3.30.8** - Java framework
- **Quarkus LangChain4J Extension** - Automatic EmbeddingModel configuration via BOM
- **LangChain4j** - Embedding generation and QdrantEmbeddingStore (versions managed by BOM)
- **OpenAI text-embedding-3-large** - Embedding model (3072-dimensional vectors)
- **Qdrant** - Vector database for similarity search
- **SnakeYAML** - YAML parsing for document access policy

## Embedding Model

The service uses **OpenAI's text-embedding-3-large** model to generate embeddings:
- **Dimensions**: 3072
- **Model**: text-embedding-3-large
- **Provider**: OpenAI (requires API key)
- **Distance Metric**: Cosine similarity (configured in Qdrant)

### Configuration

Set the `OPENAI_API_KEY` environment variable or configure it in `application.properties`:
```properties
quarkus.langchain4j.openai.api-key=${OPENAI_API_KEY:}
```

The embedding model can be changed via:
```properties
quarkus.langchain4j.openai.embedding-model.model-name=text-embedding-3-large
```

**Note:** The Quarkus LangChain4J extension automatically provides the `EmbeddingModel` bean. No manual configuration needed.

## Architecture Notes

- **Embedding Generation**: Document chunks are embedded using OpenAI's API when documents are loaded or updated
- **Vector Storage**: 3072-dimensional vectors stored in Qdrant with cosine similarity
- **Metadata Storage**: Document name, chunk index, and text are stored in Qdrant payload for retrieval
- **Search**: Returns the most similar document chunks based on ticket text queries
- **RBAC**: Document access is controlled via YAML configuration file
- **Idempotency**: All operations (upsert, delete, RBAC update) are idempotent - safe to retry
- **Startup Behavior**:
  - **Default**: Preserves existing database, does NOT reload documents
  - **With `-DDemoData=true`**: Wipes the database and reloads all documents from `src/main/resources/documents/`

## Document Structure

Documents are stored in `src/main/resources/documents/` as `.txt` files. The document name (filename) serves as the unique document ID.

The document access policy is stored in `src/main/resources/config/document_access_policy.yaml` with the following format:

```yaml
Document_Name.txt:
  read:
    - team1
    - team2
```

## Troubleshooting

**Qdrant Connection Errors:**
- Ensure Qdrant is running: `docker-compose ps`
- Check Qdrant logs: `docker-compose logs qdrant`
- Verify port 6336 (gRPC) is accessible: `telnet localhost 6336`
- **Note:** This project uses ports 6335 (HTTP) and 6336 (gRPC) to avoid conflicts with other Qdrant instances

**OpenAI API Errors:**
- Verify `OPENAI_API_KEY` is set correctly
- Check API key has sufficient credits/quota
- Ensure network can reach OpenAI API

**Documents Not Loading:**
- Check that documents exist in `src/main/resources/documents/`
- Verify document filenames match the expected format (`.txt` extension)
- Check application logs for loading errors

**RBAC Issues:**
- Verify `document_access_policy.yaml` is in `src/main/resources/config/`
- Check YAML syntax is correct
- Ensure document names in YAML match actual document filenames
