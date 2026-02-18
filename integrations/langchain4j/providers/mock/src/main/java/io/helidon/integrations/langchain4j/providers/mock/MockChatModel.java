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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Mock implementation of {@link ChatModel} for testing.
 *
 * <p>This model evaluates the list of {@link MockChatRule} defined in configuration or injected as singleton service.
 * The first rule that matches the incoming {@link ChatRequest}
 * is used to generate a mock {@link ChatResponse}. If no rule matches, a default rule is applied.</p>
 */
public class MockChatModel implements RuntimeType.Api<MockChatModelConfig>, ChatModel {

    private final MockChatModelConfig config;
    private final List<MockChatRule> rules = new CopyOnWriteArrayList<>();

    MockChatModel(MockChatModelConfig config) {
        this.config = config;
        this.rules.addAll(config.rules());
    }

    /**
     * Creates a new {@link MockChatModel} instance using the provided configuration.
     *
     * @param config the mock chat model configuration
     * @return a new {@code MockChatModel}
     */
    public static MockChatModel create(MockChatModelConfig config) {
        return new MockChatModel(config);
    }

    /**
     * Creates a new {@link MockChatModel} instance using a builder consumer to configure the model.
     *
     * @param consumer a consumer that modifies the {@link MockChatModelConfig.Builder}
     * @return a new {@code MockChatModel}
     */
    public static MockChatModel create(Consumer<MockChatModelConfig.Builder> consumer) {
        MockChatModelConfig.Builder builder = MockChatModelConfig.builder();
        consumer.accept(builder);
        return create(builder.buildPrototype());
    }

    /**
     * Returns a new {@link MockChatModelConfig.Builder} instance for building a configuration.
     *
     * @return a builder for {@link MockChatModelConfig}
     */
    public static MockChatModelConfig.Builder builder() {
        return MockChatModelConfig.builder();
    }

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        for (var rule : rules) {
            if (rule.matches(chatRequest)) {
                return rule.doMock(chatRequest);
            }
        }
        return MockChatRule.DEFAULT_RULE.doMock(chatRequest);
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
     * Resets the mock chat rules to the original set defined in the configuration.
     *
     * <p>This method clears any modifications made to the {@link #activeRules()} list and restores
     * the rules from the initial {@link MockChatModelConfig}.</p>
     */
    public void resetRules() {
        this.rules.clear();
        this.rules.addAll(this.config.rules());
    }

    /**
     * Returns the configuration prototype of this mock chat model.
     *
     * @return the {@link MockChatModelConfig} used by this model
     */
    @Override
    public MockChatModelConfig prototype() {
        return config;
    }
}
