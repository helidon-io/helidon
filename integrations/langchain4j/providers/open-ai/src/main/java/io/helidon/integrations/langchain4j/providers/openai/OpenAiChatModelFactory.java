/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.openai;

import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiChatModel;

/**
 * Factory for a configured {@link dev.langchain4j.model.openai.OpenAiChatModel}.
 *
 * @see dev.langchain4j.model.openai.OpenAiChatModel
 * @see io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(OpenAi.WEIGHT)
public class OpenAiChatModelFactory implements Service.ServicesFactory<OpenAiChatModel> {
    private final Supplier<Optional<OpenAiChatModel>> model;

    OpenAiChatModelFactory(@Service.Named(OpenAi.CHAT_MODEL) Supplier<Optional<Tokenizer>> openAiChatModelTokenizer,
                           @Service.Named(OpenAi.OPEN_AI) Supplier<Optional<Tokenizer>> openAiTokenizer,
                           Supplier<Optional<Tokenizer>> tokenizer,
                           @Service.Named(OpenAi.CHAT_MODEL) Supplier<Optional<Proxy>> openAiChatModelProxy,
                           @Service.Named(OpenAi.OPEN_AI) Supplier<Optional<Proxy>> openAiProxy,
                           Supplier<Optional<Proxy>> proxy,
                           Config config) {
        var configBuilder = OpenAiChatModelConfig.builder().config(config.get(OpenAiChatModelConfig.CONFIG_ROOT));

        this.model = () -> buildModel(configBuilder,
                                      openAiChatModelTokenizer,
                                      openAiTokenizer,
                                      tokenizer,
                                      openAiChatModelProxy,
                                      openAiProxy,
                                      proxy);
    }

    /**
     * Create the OpenAI model from its configuration.
     *
     * @param config configuration to use
     * @return a new model instance
     * @throws java.lang.IllegalStateException in case the configuration is not enabled
     */
    public static OpenAiChatModel create(OpenAiChatModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }
        var builder = OpenAiChatModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        if (!config.stop().isEmpty()) {
            builder.stop(config.stop());
        }
        config.maxTokens().ifPresent(builder::maxTokens);
        config.maxCompletionTokens().ifPresent(builder::maxCompletionTokens);
        config.presencePenalty().ifPresent(builder::presencePenalty);
        config.frequencyPenalty().ifPresent(builder::frequencyPenalty);
        if (!config.logitBias().isEmpty()) {
            builder.logitBias(config.logitBias());
        }
        config.responseFormat().ifPresent(builder::responseFormat);
        config.strictJsonSchema().ifPresent(builder::strictJsonSchema);
        config.seed().ifPresent(builder::seed);
        config.user().ifPresent(builder::user);
        config.strictTools().ifPresent(builder::strictTools);
        config.parallelToolCalls().ifPresent(builder::parallelToolCalls);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.tokenizer().ifPresent(builder::tokenizer);
        config.proxy().ifPresent(builder::proxy);
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiChatModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, OpenAi.OPEN_AI_QUALIFIER));

    }

    private static Optional<OpenAiChatModel> buildModel(OpenAiChatModelConfig.Builder configBuilder,
                                                        Supplier<Optional<Tokenizer>> openAiModelTokenizer,
                                                        Supplier<Optional<Tokenizer>> openAiTokenizer,
                                                        Supplier<Optional<Tokenizer>> tokenizer,
                                                        Supplier<Optional<Proxy>> openAiModelProxy,
                                                        Supplier<Optional<Proxy>> openAiProxy,
                                                        Supplier<Optional<Proxy>> proxy) {
        if (!configBuilder.enabled()) {
            return Optional.empty();
        }
        openAiModelTokenizer.get()
                .or(openAiTokenizer)
                .or(tokenizer)
                .ifPresent(configBuilder::tokenizer);

        openAiModelProxy.get()
                .or(openAiProxy)
                .or(proxy)
                .ifPresent(configBuilder::proxy);

        openAiModelProxy.get()
                .or(openAiProxy)
                .or(proxy)
                .ifPresent(configBuilder::proxy);
        return Optional.of(create(configBuilder.build()));
    }
}
