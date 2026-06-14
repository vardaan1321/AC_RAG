package com.altametrics.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.altametrics.model.AskRequest;
import com.altametrics.model.AskResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

	private final ChatClient.Builder chatClientBuilder;
	private final VectorStore vectorStore;

	@Value("${rag.top-k:5}")
	private int defaultTopK;

	private static final String RAG_PROMPT_TEMPLATE = """
			You are a helpful assistant that answers questions based ONLY on the provided context.

			If the answer is not found in the context, say:
			"I could not find relevant information in the uploaded documents."

			Do NOT use outside knowledge.
			Be concise and accurate.

			Context:
			{context}

			Question:
			{question}

			Answer:
			""";
	private static final String QUERY_REWRITE_PROMPT = """
			Rewrite the user's question into a standalone question.

			Use the conversation history only to resolve references like:
			- he / she / it / they
			- the previous one

			Do NOT answer the question.
			Return only the rewritten question.
			""";

	public AskResponse ask(AskRequest request) {
		long startTime = System.currentTimeMillis();
		int topK = this.resolveTopK(request);
		log.info("Received question: '{}', topK: {}, sessionID: {}", request.getQuestion(), topK,
				request.getSessionID());
		List<Document> documents = this.searchRelevantDocuments(request);
		if (documents.isEmpty()) {
			return this.buildNoResultResponse(request, startTime);
		}
		String context = this.buildContext(documents);
		List<String> sources = this.extractSources(documents);
		String answer = this.generateAnswer(request.getQuestion(), context, request.getSessionID());
		long elapsed = System.currentTimeMillis() - startTime;
		log.info("RAG answer generated in {} ms using {} source(s)", elapsed, sources.size());
		return new AskResponse(request.getQuestion(), answer, sources, elapsed);
	}

	private int resolveTopK(AskRequest request) {
		return request.getTopK() > 0 ? request.getTopK() : defaultTopK;
	}

	private String buildContext(List<Document> documents) {
		return documents.stream().map(Document::getText).collect(Collectors.joining("\n\n---\n\n"));
	}

	private List<String> extractSources(List<Document> documents) {
		return documents.stream().map(doc -> (String) doc.getMetadata().getOrDefault("source_file", "unknown"))
				.distinct().toList();
	}

	private String buildPrompt(String question, String context) {
		return new PromptTemplate(RAG_PROMPT_TEMPLATE).render(Map.of("context", context, "question", question));
	}

	private String generateAnswer(String question, String context, String sessionId) {
		String prompt = this.buildPrompt(question, context);
		ChatClient chatClient = chatClientBuilder.build();
		if (sessionId == null || sessionId.isBlank()) {
			return chatClient.prompt().user(prompt).call().content();
		}
		return chatClient.prompt().user(prompt)
				.advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, sessionId)).call().content();
	}

	private AskResponse buildNoResultResponse(AskRequest request, long startTime) {
		return new AskResponse(request.getQuestion(),
				"I could not find relevant information in the uploaded documents.", List.of(),
				System.currentTimeMillis() - startTime);
	}

	private String rewriteQuestion(String question, String sessionId) {
		if (sessionId == null || sessionId.isBlank()) {
			return question;
		}
		String rewrittenQuestion = chatClientBuilder.build().prompt().system(QUERY_REWRITE_PROMPT).user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId)).call().content();
		log.info("Original Question : {}", question);
		log.info("Rewritten Question: {}", rewrittenQuestion);
		return rewrittenQuestion;
	}

	private List<Document> searchRelevantDocuments(AskRequest request) {
		List<Document> relevantDocs = null;
		if (request.getSessionID() == null || request.getSessionID().isBlank()) {
			relevantDocs = vectorStore.similaritySearch(
					SearchRequest.builder().query(request.getQuestion()).topK(request.getTopK()).build());
			return relevantDocs;
		}
		String searchQuery = this.rewriteQuestion(request.getQuestion(), request.getSessionID());
		relevantDocs = vectorStore
				.similaritySearch(SearchRequest.builder().query(searchQuery).topK(request.getTopK()).build());
		return relevantDocs;

	}

	private static final String QUERY_REWRITE_PROMPT1 = """
			You are a search query generator for an enterprise RAG system.

			Convert the user's question into a retrieval query.

			Rules:
			- Resolve pronouns using conversation history.
			- Replace references with actual entity names.
			- Include important nouns and business terms.
			- Remove filler words.
			- Do not answer the question.
			- Return only search keywords.
			""";

	private List<Document> searchRelevantDocuments(String question, int topK) {
		return vectorStore.similaritySearch(SearchRequest.builder().query(question).topK(topK).build());
	}
}
