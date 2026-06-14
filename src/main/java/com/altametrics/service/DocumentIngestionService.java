package com.altametrics.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

	private final VectorStore vectorStore;
	private final TokenTextSplitter textSplitter;

	public IngestResponse ingest(MultipartFile file) throws IOException {
		log.info("Ingesting file: {} ({})", file.getOriginalFilename(), file.getContentType());
		Resource resource = createResource(file);
		List<Document> rawDocuments = loadDocuments(resource, file.getContentType(), file.getOriginalFilename());
		log.debug("Loaded {} raw document(s)", rawDocuments.size());
		List<Document> chunks = splitDocuments(rawDocuments);
		addMetadata(chunks, file);
		storeDocuments(chunks);
		return buildResponse(file.getOriginalFilename(), chunks.size());
	}

	private Resource createResource(MultipartFile file) throws IOException {
		return new ByteArrayResource(file.getBytes()) {
			@Override
			public String getFilename() {
				return file.getOriginalFilename();
			}
		};
	}

	private List<Document> splitDocuments(List<Document> documents) {
		List<Document> chunks = textSplitter.apply(documents);
		log.debug("Split into {} chunks", chunks.size());
		return chunks;
	}

	private void addMetadata(List<Document> chunks, MultipartFile file) {
		chunks.forEach(chunk -> chunk.getMetadata().putAll(Map.of("source_file", file.getOriginalFilename(),

				"content_type", file.getContentType() != null ? file.getContentType() : "unknown")));
	}

	private void storeDocuments(List<Document> chunks) {
		vectorStore.add(chunks);
		log.info("Stored {} chunk(s) in vector store", chunks.size());
	}

	private IngestResponse buildResponse(String fileName, int chunkCount) {
		return new IngestResponse(fileName, chunkCount, "SUCCESS", "Document ingested and embedded successfully");
	}

	private List<Document> loadDocuments(Resource resource, String contentType, String fileName) {
		if (isPdf(contentType, fileName)) {
			return readPdf(resource);
		}
		return readWithTika(resource);
	}

	private boolean isPdf(String contentType, String fileName) {
		return (contentType != null && contentType.contains("pdf"))
				|| (fileName != null && fileName.toLowerCase().endsWith(".pdf"));
	}

	private List<Document> readPdf(Resource resource) {
		PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
				.withPageExtractedTextFormatter(
						new ExtractedTextFormatter.Builder().withNumberOfBottomTextLinesToDelete(0).build())
				.withPagesPerDocument(1).build();

		return new PagePdfDocumentReader(resource, config).get();
	}

	private List<Document> readWithTika(Resource resource) {
		return new TikaDocumentReader(resource).get();
	}
}
