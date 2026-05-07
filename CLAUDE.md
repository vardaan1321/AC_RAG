# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
./mvnw clean install

# Run (requires external services — see below)
./mvnw spring-boot:run

# Package JAR
./mvnw clean package

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=RagApplicationTests
```

Application starts at `http://localhost:8080`.

## Required External Services

The application will fail to start without these:

| Service | Address | Purpose |
|---|---|---|
| Ollama | `http://localhost:11434` | LLM (`llama3`) + embeddings (`nomic-embed-text`) |
| Qdrant | `localhost:6334` (gRPC) | Vector store, collection `rag-docs` |

## Architecture

This is a Spring Boot 3.5 + Spring AI RAG (Retrieval-Augmented Generation) application that lets users upload documents and ask questions answered using those documents as context.

**Two-phase flow:**

1. **Ingest**: `DocumentController` → `DocumentIngestionService` reads uploaded files (PDF via dedicated reader; DOCX/HTML/PPTX/TXT via Apache Tika), splits into chunks using `TokenTextSplitter` (chunk size 500, overlap 100), attaches `source_file`/`content_type` metadata, then embeds and stores in Qdrant.

2. **Query**: `RagController` → `RagService` embeds the user question, retrieves top-k similar chunks from Qdrant, builds a context string, calls Ollama (`llama3`) with a prompt that constrains the LLM to only answer from the provided context, and returns the answer with source filenames and processing time.

**Key classes:**
- `RagConfig` — defines the `TokenTextSplitter` bean (reads chunk params from `application.yml`)
- `DocumentIngestionService` — format detection, chunking, embedding, Qdrant storage
- `RagService` — similarity search, prompt construction, LLM call, response assembly
- `AskRequest` / `AskResponse` / `IngestResponse` — request/response DTOs

## REST API

```
POST /api/documents/ingest          # single file upload (multipart)
POST /api/documents/ingest/batch    # multiple files upload
POST /api/rag/ask                   # { "question": "...", "topK": 5 }
GET  /api/rag/ask?q=<question>      # browser-friendly variant
GET  /                              # health + endpoint listing
```

## Configuration

All tunable parameters live in [src/main/resources/application.yml](src/main/resources/application.yml):
- `spring.ai.ollama.chat.model` — LLM model name
- `spring.ai.ollama.embedding.model` — embedding model name
- `spring.ai.vectorstore.qdrant.*` — Qdrant host, port, collection
- `rag.chunk-size` / `rag.chunk-overlap` — document splitting
- `rag.top-k` — default number of retrieved chunks per query
