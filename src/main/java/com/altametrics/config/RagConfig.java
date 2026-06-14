package com.altametrics.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

	@Value("${rag.chunk-size:500}")
	private int chunkSize;

	@Bean
	TokenTextSplitter tokenTextSplitter() {
	    return TokenTextSplitter.builder()
	            .withChunkSize(chunkSize)
	            .build();
	}

	@Bean
	ChatMemory chatMemory() {
		return MessageWindowChatMemory.builder().maxMessages(20).build();
	}
}
