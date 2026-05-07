package com.altametrics.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.altametrics.model.AskRequest;
import com.altametrics.model.AskResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;

    @Value("${rag.top-k:5}")
    private int defaultTopK;

    // ── System prompt template ────────────────────────────────────
    private static final String RAG_PROMPT_TEMPLATE = """
        You are a helpful assistant that answers questions based ONLY on the provided context.
        
        If the answer is not found in the context, say:
        "I could not find relevant information in the uploaded documents."
        
        Do NOT use outside knowledge. Be concise and accurate.
        
        Context:
        {context}
        
        Question: {question}
        
        Answer:
        """;

    /**
     * Full RAG pipeline:
     * 1. Embed the user question
     * 2. Search Qdrant for top-k similar chunks
     * 3. Build prompt with retrieved context
     * 4. Call Ollama LLM and return the answer
     */
    public AskResponse ask(AskRequest request) {
        long start = System.currentTimeMillis();
        int topK = request.getTopK() > 0 ? request.getTopK() : defaultTopK;

        log.info("RAG query: '{}' (top-k={})", request.getQuestion(), topK);

        // Step 1 & 2: Embed question + similarity search in Qdrant
        List<Document> relevantDocs = vectorStore.similaritySearch(
            SearchRequest.builder().query(request.getQuestion()).topK(topK).build()
        );

        //log.info("Qdrant returned {} relevant chunks for the question", relevantDocs.size());
        if (relevantDocs.isEmpty()) {
            log.warn("No relevant documents found for: '{}'", request.getQuestion());
            return new AskResponse(
                request.getQuestion(),
                "I could not find relevant information in the uploaded documents.",
                List.of(),
                System.currentTimeMillis() - start
            );
        }

        log.debug("Retrieved {} chunks from Qdrant", relevantDocs.size());

        // Step 3: Build context string from retrieved chunks
        String context = relevantDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n\n---\n\n"));

        // Collect source file names for transparency
        log.info("Sources for retrieved chunks:"+context);
        List<String> sources = relevantDocs.stream()
            .map(doc -> (String) doc.getMetadata().getOrDefault("source_file", "unknown"))
            .distinct()
            .collect(Collectors.toList());
        log.info("Sources used for answering: {}", sources);
        // Step 4: Call LLM with context + question
        PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT_TEMPLATE);
        String prompt = promptTemplate.render(Map.of(
            "context", context,
            "question", request.getQuestion()
        ));
        log.info("Constructed prompt for LLM:\n{}", prompt);
        String answer = chatClientBuilder.build()
            .prompt()
            .user(prompt)
            .call()
            .content();

        long elapsed = System.currentTimeMillis() - start;
        log.info("RAG answer generated in {}ms from {} source(s)", elapsed, sources.size());

        return new AskResponse(request.getQuestion(), answer, sources, elapsed);
    }
}
