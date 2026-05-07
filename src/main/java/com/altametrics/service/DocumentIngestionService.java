package com.altametrics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.altametrics.model.IngestResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    /**
     * Ingests an uploaded file into Qdrant.
     * Supports: PDF, DOCX, TXT, HTML, and more via Apache Tika.
     */
    public IngestResponse ingest(MultipartFile file) throws IOException {
        log.info("Ingesting file: {} ({})", file.getOriginalFilename(), file.getContentType());

        // 1. Convert upload to Spring Resource
        Resource resource = new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };

        // 2. Load documents based on file type
        List<Document> rawDocs = loadDocuments(resource, file.getContentType(), file.getOriginalFilename());
        log.debug("Loaded {} raw document(s) from {}", rawDocs.size(), file.getOriginalFilename());

        // 3. Split into chunks
        List<Document> chunks = textSplitter.apply(rawDocs);
        log.debug("Split into {} chunks", chunks.size());

        // 4. Add source metadata to every chunk
        chunks.forEach(chunk -> chunk.getMetadata().putAll(Map.of(
            "source_file", file.getOriginalFilename(),
            "content_type", file.getContentType() != null ? file.getContentType() : "unknown"
        )));

        // 5. Embed + store in Qdrant (Spring AI handles embedding automatically)
        vectorStore.add(chunks);
        log.info("Stored {} chunks from '{}' into Qdrant", chunks.size(), file.getOriginalFilename());

        return new IngestResponse(
            file.getOriginalFilename(),
            chunks.size(),
            "SUCCESS",
            "Document ingested and embedded successfully"
        );
    }

    private List<Document> loadDocuments(Resource resource, String contentType, String fileName) {
        // Use dedicated PDF reader for better accuracy on PDFs
        if (contentType != null && contentType.contains("pdf") ||
            (fileName != null && fileName.toLowerCase().endsWith(".pdf"))) {

            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(new ExtractedTextFormatter.Builder()
                    .withNumberOfBottomTextLinesToDelete(0)
                    .build())
                .withPagesPerDocument(1)
                .build();

            return new PagePdfDocumentReader(resource, config).get();
        }

        // Use Apache Tika for everything else (DOCX, TXT, HTML, PPTX...)
        return new TikaDocumentReader(resource).get();
    }
}
