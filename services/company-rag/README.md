# Company Internal Documents RAG

A Quarkus application for storing and searching company internal documents using RAG (Retrieval-Augmented Generation) with LangChain4j and Oracle AI vector storage.

## Overview

This RAG system allows you to:
- Store and manage company internal documents with smart chunking strategies
- Search documents based on ticket text queries
- Manage document access using RBAC (Role-Based Access Control)
- View all documents and their team permissions in a web dashboard

## Prerequisites

- Java 25
- Maven 3.8+
- Docker (for Oracle 26 AI to run locally)
- OpenAI API key (for embeddings)

## Setup

1. **Start OracleDB and Docling first (unless you use this services without Docling, see later):**
   ```bash
   docker-compose up -d
   ```
   This starts:
   - Oracle on `localhost:1522`
   - Docling Serve on `localhost:5001`

   If you want to start only Docling server:
   ```bash
   docker-compose up -d docling
   ```

2. **Set your OpenAI API key:**
   ```bash
   export OPENAI_API_KEY=your-api-key-here
   ```

3. **Run the application:**
   - Dev mode:
   ```bash
   mvn quarkus:dev -Dsync-demo-data=true
   ```
   - Normal mode:
   ```bash
   mvn package
   java -Dsync-demo-data=true -jar target/quarkus-app/quarkus-run.jar
   ```
   This sync flag embeds documents from `company-documents/` on startup. For the bundled demo docs this is typically under $0.01 in embedding API cost.
   If you do **not** want startup embedding sync:
   - Dev mode: `mvn quarkus:dev`
   - Normal mode: `mvn package && java -jar target/quarkus-app/quarkus-run.jar`

The application will run on port **8084**.

### Run with Docling preprocessing

If you want PDF/DOCX/PPTX/XLSX preprocessing (including table/figure transcription) before chunking, keep Docling up from step 1 and run:

1. **Set preprocessing mode when starting company-rag:**
   - Dev mode:
   ```bash
   mvn quarkus:dev -Dsync-demo-data=true -Ddocument.preprocessing.mode=docling
   ```
   - Normal mode:
   ```bash
   mvn package
   java -Dsync-demo-data=true -Ddocument.preprocessing.mode=docling -jar target/quarkus-app/quarkus-run.jar
   ```
2. **(Optional) Override Docling URL if needed:**
   - Dev mode:
   ```bash
   mvn quarkus:dev \
     -Dsync-demo-data=true \
     -Ddocument.preprocessing.mode=docling \
       -Ddocument.preprocessing.docling.base-url=http://localhost:5001
   ```
   - Normal mode:
   ```bash
   mvn package
   java \
     -Dsync-demo-data=true \
     -Ddocument.preprocessing.mode=docling \
     -Ddocument.preprocessing.docling.base-url=http://localhost:5001 \
     -jar target/quarkus-app/quarkus-run.jar
   ```

### Docling preview CLI (standalone observer)

Start Docling server first:

```bash
docker-compose up -d docling
```

Then, if you want to inspect what Docling produces for a specific file in `company-documents/` without starting the full app flow, run:

```bash
mvn -q -DskipTests compile exec:java \
  -Dexec.mainClass=com.example.document.tool.DoclingPreviewCli \
  -Dexec.args="Billing_Payment_Reliability_Report_26.pdf $OPENAI_API_KEY"
```

Optional: `--keep-image-blobs` to embed base64 images in output (default: PLACEHOLDER mode, no blobs).

### Document ingestion: picture description (VLM)

For document ingestion (embedding sync), picture description uses a single VLM endpoint. Configure in `application.properties`:

- `document.preprocessing.docling.vlm.do-picture-description=true`: enable VLM for images
- `document.preprocessing.docling.vlm.url`: API endpoint (e.g. OpenAI or Ollama)
- `document.preprocessing.docling.vlm.model`: model name
- `document.preprocessing.docling.vlm.api-key`: optional; for OpenAI use `${OPENAI_API_KEY:}`; for Ollama leave empty

**OpenAI:** `url=https://api.openai.com/v1/chat/completions`, `model=gpt-4o-mini`, `api-key=${OPENAI_API_KEY:}`

**Ollama (local):**

1. Install Ollama: https://ollama.com
2. Pull a vision model: `ollama pull qwen2.5vl:3b`
3. Start the server: `ollama serve` (often runs automatically; ensure it listens on port 11434)
4. Set `url=http://localhost:11434/v1/chat/completions`, `model=qwen2.5vl:3b`, `api-key=` (empty)



**Important:** 
- Make sure the Oracle database is running before starting the application, otherwise you'll get connection errors.
- Startup embedding sync is opt-in via `-Dsync-demo-data=true`. Without it, startup does not embed folder documents.
- Embedding sync calls the embedding provider API and incurs cost.
- To wipe the database first and then sync/embed all documents from `company-documents/`, use:
  ```bash
  mvn quarkus:dev -Ddemo.data.load=true
  ```
4. By default, tests that do not end in `IT` can be run in dev mode. The integration tests require a database setup with demo data, and you can run them via 

  ```
  mvn -Dtest=DocumentResourceIT test
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
- **Custom Strategies**: Defined in `src/main/resources/config/document_splitting_rule.yaml`. At runtime, you may override the location of this document by providing the absolute path to it in `demo.config.split.location` variable.

### Configuration

Edit `src/main/resources/application.properties` to configure default chunking:
```properties
document.chunking.default.strategy=recursive
document.chunking.default.chunk-size=300
document.preprocessing.mode=pure-text
document.preprocessing.docling.base-url=http://localhost:5001
```

`document.preprocessing.mode` options:
- `pure-text` (default): ingest only `.txt` and `.md` files as plain text; skip other extensions
- `docling`: attempt Docling for `.pdf`, `.docx`, `.pptx`, `.xlsx`, and `.md`; `.txt` stays plain-text; skip unsupported extensions


Storage and sync behavior:
- `demo.dir.location` points to a writable filesystem folder (default: `company-documents`).
- Startup embedding loads files from that folder only.
- `upsert` writes/overwrites the file in that folder and then re-embeds by `documentName`.
- `upload` (`multipart/form-data`) writes/overwrites the uploaded file in that folder and re-embeds it.
- `DELETE /api/documents/{documentName}` removes both file and embeddings.
- `GET /api/documents/content/{documentName}` reads the file from that folder.
- `GET /api/documents/download/{documentName}` returns the raw stored file bytes for any document type.
- UI previews only `.txt` and `.md`; other file types are shown with their extension and downloaded on click.


## Document Access Policy

Document access is managed via RBAC (Role-Based Access Control) stored in `company-documents/config/document_access_policy.yaml` (writable).

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
- `rbacTeams` (optional): List of teams with access. If omitted and the file already exists, existing RBAC is preserved; if omitted for a new file, it is company-wide.

### 3. Upload Document File
**POST** `/api/documents/upload` (multipart/form-data)

Uploads a new or updated document file, stores it in `company-documents/`, and (re)embeds it.

**Form fields:**
- `documentName` (required): File name to store and index
- `file` (required): Raw file bytes
- `rbacTeams` (optional): Comma-separated list of teams. If omitted and the file already exists, existing RBAC is preserved; if omitted for a new file, it is company-wide.

### 4. Delete Document
**DELETE** `/api/documents/{documentName}`

Deletes a document and all its embeddings (idempotent).

**Path Parameters:**
- `documentName` (required)

**Response:**
```json
{
  "status": "OK"
}
```

### 5. Update RBAC
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

### 6. Get All Documents
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

### 7. Get Activity Logs
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

### 8. Get Document Content
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

### 9. Download Raw Document File
**GET** `/api/documents/download/{documentName}`

Returns the original stored file bytes (for example PDF/DOCX/PPTX/XLSX).

### 10. Get Config
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
curl -X DELETE http://localhost:8084/api/documents/Test_Document.txt
```

### 5. Upload a Document File (multipart)

```bash
curl -X POST http://localhost:8084/api/documents/upload \
  -F "documentName=Quarterly_Report.pdf" \
  -F "rbacTeams=finance,leadership" \
  -F "file=@/absolute/path/to/Quarterly_Report.pdf"
```

### 6. Get All Documents

```bash
curl http://localhost:8084/api/documents/all
```

### 7. Get Document Content (txt/md preview)

```bash
curl http://localhost:8084/api/documents/content/Approved_Response_Templates.txt
```

### 8. Get Raw Document File

```bash
curl -OJ http://localhost:8084/api/documents/download/Quarterly_Report.pdf
```

**Expected Response:**
```json
{
  "content": "Template: Appointment Cancellation or Reschedule Acknowledgement\nHello,\nThanks for reaching out..."
}
```

## Data Persistence

- **Embeddings are stored in Oracle AI 26ai** and persist across application restarts
- **Document metadata** (document name, chunk index) is stored in Oracle embedding table metadata
- **Document access policy** is stored in `company-documents/config/document_access_policy.yaml` (writable, persisted on RBAC changes)
- Documents are automatically re-embedded on startup from `company-documents/`

## Configuration

Edit `src/main/resources/application.properties` to configure:
- Oracle datasource URL/credentials (default: `jdbc:oracle:thin:@localhost:1522/freepdb1`)
- Oracle embedding table and metadata column (`oracleai.embedding.*`)
- OpenAI API key (required for embeddings)
- Default chunking strategy and chunk size
- Documents storage folder (`demo.dir.location`, default `company-documents`)

## Technology Stack

- **Quarkus 3.30.8** - Java framework
- **Quarkus LangChain4J Extension** - Automatic EmbeddingModel configuration via BOM
- **LangChain4j** - Embedding generation and Oracle embedding store integration (versions managed by BOM)
- **OpenAI text-embedding-3-large** - Embedding model (3072-dimensional vectors)
- **Oracle AI 26ai** - Vector database for similarity search
- **SnakeYAML** - YAML parsing for document access policy

## Embedding Model

The service uses **OpenAI's text-embedding-3-large** model to generate embeddings:
- **Dimensions**: 3072
- **Model**: text-embedding-3-large
- **Provider**: OpenAI (requires API key)
- **Distance Metric**: Cosine similarity (configured via Oracle vector index settings)

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
- **Vector Storage**: 3072-dimensional vectors stored in Oracle AI 26ai with cosine similarity
- **Metadata Storage**: Document name, chunk index, and text are stored in Oracle embedding table metadata for retrieval
- **Search**: Returns the most similar document chunks based on ticket text queries
- **RBAC**: Document access is controlled via YAML configuration file
- **Idempotency**: All operations (upsert, delete, RBAC update) are idempotent - safe to retry
- **Startup Behavior**:
  - **Default**: Preserves existing database and does NOT sync/embed folder documents on startup
  - **With `-Dsync-demo-data=true`**: Syncs and embeds all documents from `company-documents/`
  - **With `-Ddemo.data.load=true`**: Wipes the database first, then syncs/embeds all documents

## Document Structure

Documents are stored in a writable local folder configured by `demo.dir.location` (default: `company-documents`). The document name (filename) serves as the unique document ID.

The document access policy is stored in `company-documents/config/document_access_policy.yaml` with the following format:

```yaml
Document_Name.txt:
  read:
    - team1
    - team2
```
