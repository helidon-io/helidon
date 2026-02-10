/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.langchain4j.providers.mock;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;

/**
 * Represents a rule for mocking chat interactions.
 *
 * <p>A {@code MockChatRule} determines whether it matches a given {@link ChatRequest}
 * and can generate a mock {@link ChatResponse}.</p>
 */
public interface MockChatRule extends RuntimeType.Api<MockChatRuleConfig> {

    /**
     * Default rule that matches any request.
     */
    MockChatRule DEFAULT_RULE = req -> true;

    /**
     * Returns a new {@link MockChatRuleConfig.Builder} for building rule configurations.
     *
     * @return a builder instance
     */
    static MockChatRuleConfig.Builder builder() {
        return MockChatRuleConfig.builder();
    }

    /**
     * Creates a {@link MockChatRule} using a builder consumer to configure the rule.
     *
     * @param consumer a consumer that modifies the {@link MockChatRuleConfig.Builder}
     * @return a new {@code MockChatRule}
     */
    static MockChatRule create(Consumer<MockChatRuleConfig.Builder> consumer) {
        return builder().update(consumer).build();
    }

    /**
     * Creates a {@link MockChatRule} from the given configuration.
     *
     * @param config the rule configuration
     * @return a new {@code MockChatRule}
     */
    static MockChatRule create(Config config) {
        return MockChatRule.create(MockChatRuleConfig.create(config));
    }

    /**
     * Creates a {@link MockChatRule} from the given configuration.
     *
     * @param config the rule configuration
     * @return a new {@code MockChatRule}
     */
    static MockChatRule create(MockChatRuleConfig config) {
        return new MockChatRuleImpl(config);
    }

    /**
     * Determines whether this rule matches the given chat request.
     *
     * @param req the chat request to evaluate
     * @return {@code true} if the rule matches, {@code false} otherwise
     */
    boolean matches(ChatRequest req);

    /**
     * Returns the prototype configuration for this rule.
     *
     * <p>Default implementation returns {@code null} and should be overridden by concrete rules.</p>
     *
     * @return the {@link MockChatRuleConfig} prototype, or {@code null} if not applicable
     */
    @Override
    default MockChatRuleConfig prototype() {
        return MockChatRuleConfig.builder().buildPrototype();
    }

    /**
     * Creates a mock {@link ChatResponse} for the given {@link ChatRequest}.
     *
     * @param req the chat request to mock
     * @return chat response
     */
    default ChatResponse doMock(ChatRequest req) {
        var input = Math.max(concatMessages(req.messages()).length(), 4) / 4;
        var mockedResponse = mock(req);
        var output = Math.max(mockedResponse.length(), 4) / 4;
        return ChatResponse.builder()
                .modelName("helidon/mock-model")
                .finishReason(FinishReason.STOP)
                .tokenUsage(new TokenUsage(input, output, input + output))
                .aiMessage(AiMessage.builder()
                                   .text(mockedResponse)
                                   .build())
                .build();
    }

    /**
     * Creates a mock streaming chat response for the given request.
     *
     * <p>This default implementation generates a full mock {@link ChatResponse} using
     * {@link #doMock(ChatRequest)} and delivers it to the provided
     * {@link StreamingChatResponseHandler} via
     * {@link StreamingChatResponseHandler#onCompleteResponse(ChatResponse)}.</p>
     *
     * @param chatRequest the chat request to mock
     * @param handler     the streaming response handler that receives the completed mock response
     */
    default void doMock(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        var response = doMock(chatRequest);
        Arrays.stream(response.aiMessage().text().splitWithDelimiters("\\s", 0))
                .forEach(handler::onPartialResponse);
        handler.onCompleteResponse(response);
    }

    /**
     * Produces a mock textual response for the given {@link ChatRequest}.
     *
     * @param req the chat request
     * @return the mocked response string
     */
    default String mock(ChatRequest req) {
        return mock(concatMessages(req.messages()));
    }

    /**
     * Generates a mock response for a concatenated request string.
     *
     * <p>The default implementation returns a constant placeholder string.</p>
     *
     * @param concatenatedReq the concatenated request messages
     * @return a mock response string
     */
    default String mock(String concatenatedReq) {
        return "Mock response!";
    }

    /**
     * Concatenates the textual content of the given {@link ChatMessage} collection into a single {@code String}.
     *
     * <p>By default, the method extracts text based on the message type:
     * {@link UserMessage} → {@code singleText()},
     * {@link AiMessage} → {@code text()},
     * {@link SystemMessage} → {@code text()}.
     * Any other types are ignored.</p>
     *
     * @param messages the collection of {@link ChatMessage}s to concatenate
     * @return the concatenated message text
     */
    default String concatMessages(Collection<ChatMessage> messages) {
        return messages.stream()
                .map(m -> switch (m.type()) {
                    case USER -> ((UserMessage) m).singleText();
                    case AI -> ((AiMessage) m).text();
                    case SYSTEM -> ((SystemMessage) m).text();
                    default -> null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.joining());
    }
}
