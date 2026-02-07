/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

/**
 * A mock implementation of a streaming chat model for testing and development.
 * <p>
 * This model evaluates incoming chat requests against a set of {@link MockChatRule}s and
 * streams the corresponding mock responses. If no rule matches, a default mock response is used.
 * <p>
 * Rules are initialized from the provided {@link MockStreamingChatModelConfig} and can be inspected
 * and modified at runtime using {@link #activeRules()} and {@link #resetRules()}.
 */
public class MockStreamingChatModel implements RuntimeType.Api<MockStreamingChatModelConfig>, StreamingChatModel {

    private final MockStreamingChatModelConfig config;
    private final List<MockChatRule> rules = new CopyOnWriteArrayList<>();

    MockStreamingChatModel(MockStreamingChatModelConfig config) {
        this.config = config;
        this.rules.addAll(config.rules());
    }

    /**
     * Creates a new {@link MockStreamingChatModel} instance using the provided configuration.
     *
     * @param config the mock chat model configuration
     * @return a new {@code MockStreamingChatModel}
     */
    public static MockStreamingChatModel create(MockStreamingChatModelConfig config) {
        return new MockStreamingChatModel(config);
    }

    /**
     * Creates a new {@link MockStreamingChatModel} instance using a builder consumer to configure the model.
     *
     * @param consumer a consumer that modifies the {@link MockStreamingChatModelConfig.Builder}
     * @return a new {@code MockStreamingChatModel}
     */
    public static MockStreamingChatModel create(Consumer<MockStreamingChatModelConfig.Builder> consumer) {
        MockStreamingChatModelConfig.Builder builder = MockStreamingChatModelConfig.builder();
        consumer.accept(builder);
        return create(builder.buildPrototype());
    }

    /**
     * Returns a new {@link MockStreamingChatModelConfig.Builder} instance for building a configuration.
     *
     * @return a builder for {@link MockStreamingChatModelConfig}
     */
    public static MockStreamingChatModelConfig.Builder builder() {
        return MockStreamingChatModelConfig.builder();
    }

    /**
     * Processes a chat request and streams the mock response to the provided handler.
     * <p>
     * The request is evaluated against the configured {@link MockChatRule}s in order; the first matching rule
     * is used to produce and stream the response. If no rule matches, the default rule is applied.
     *
     * @param chatRequest the incoming chat request to evaluate
     * @param handler     the response handler that receives streamed response chunks and completion events
     */
    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        for (var rule : rules) {
            if (rule.matches(chatRequest)) {
                rule.doMock(chatRequest, handler);
                return;
            }
        }
        MockChatRule.DEFAULT_RULE.doMock(chatRequest, handler);
    }

    /**
     * Returns the list of {@link MockChatRule}s currently used by this model.
     * <p>
     * The returned list is the live internal list; modifications to it will affect
     * the model's behavior. Use {@link #resetRules()} to restore the original
     * configuration.
     *
     * @return mutable list of mock chat rules
     */
    public List<MockChatRule> activeRules() {
        return this.rules;
    }

    /**
     * Resets the active mock chat rules to the original set defined by the initial configuration.
     * <p>
     * This method clears any modifications made to the {@link #activeRules()} list and restores
     * the rules from the initial {@link MockStreamingChatModelConfig}.
     * </p>
     */
    public void resetRules() {
        this.rules.clear();
        this.rules.addAll(this.config.rules());
    }

    /**
     * Returns the configuration prototype of this mock chat model.
     *
     * @return the {@link MockStreamingChatModelConfig} used by this model
     */
    @Override
    public MockStreamingChatModelConfig prototype() {
        return config;
    }
}
