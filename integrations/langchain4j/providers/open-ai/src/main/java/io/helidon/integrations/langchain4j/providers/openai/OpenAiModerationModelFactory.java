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

import dev.langchain4j.model.openai.OpenAiModerationModel;

/**
 * Factory for creating a configured {@link OpenAiModerationModel}.
 *
 * @see dev.langchain4j.model.openai.OpenAiModerationModel
 * @see OpenAiModerationModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(OpenAi.WEIGHT)
public class OpenAiModerationModelFactory implements Service.ServicesFactory<OpenAiModerationModel> {
    private final Supplier<Optional<OpenAiModerationModel>> model;

    OpenAiModerationModelFactory(@Service.Named(OpenAi.MODERATION_MODEL) Supplier<Optional<Proxy>> openAiChatModelProxy,
                                 @Service.Named(OpenAi.OPEN_AI) Supplier<Optional<Proxy>> openAiProxy,
                                 Supplier<Optional<Proxy>> proxy,
                                 Config config) {
        var configBuilder = OpenAiModerationModelConfig.builder()
                .config(config.get(OpenAiModerationModelConfigBlueprint.CONFIG_ROOT));

        this.model = () -> buildModel(configBuilder,
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
    public static OpenAiModerationModel create(OpenAiModerationModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }

        var builder = OpenAiModerationModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.proxy().ifPresent(builder::proxy);
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiModerationModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, OpenAi.OPEN_AI_QUALIFIER));
    }

    private static Optional<OpenAiModerationModel> buildModel(OpenAiModerationModelConfig.Builder configBuilder,
                                                              Supplier<Optional<Proxy>> openAiModelProxy,
                                                              Supplier<Optional<Proxy>> openAiProxy,
                                                              Supplier<Optional<Proxy>> proxy) {
        if (!configBuilder.enabled()) {
            return Optional.empty();
        }

        openAiModelProxy.get()
                .or(openAiProxy)
                .or(proxy)
                .ifPresent(configBuilder::proxy);
        return Optional.of(create(configBuilder.build()));
    }
}
