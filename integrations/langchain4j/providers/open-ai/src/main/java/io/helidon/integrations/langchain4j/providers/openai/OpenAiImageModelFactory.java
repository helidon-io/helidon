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

import dev.langchain4j.model.openai.OpenAiImageModel;

/**
 * Factory for a configured {@link OpenAiImageModel}.
 *
 * @see OpenAiImageModel
 * @see OpenAiImageModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(OpenAi.WEIGHT)
public class OpenAiImageModelFactory implements Service.ServicesFactory<OpenAiImageModel> {
    private final Supplier<Optional<OpenAiImageModel>> model;

    OpenAiImageModelFactory(@Service.Named(OpenAi.IMAGE_MODEL) Supplier<Optional<Proxy>> openAiChatModelProxy,
                            @Service.Named(OpenAi.OPEN_AI) Supplier<Optional<Proxy>> openAiProxy,
                            Supplier<Optional<Proxy>> proxy,
                            Config config) {
        var configBuilder = OpenAiImageModelConfig.builder().config(config.get(OpenAiImageModelConfigBlueprint.CONFIG_ROOT));

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
    public static OpenAiImageModel create(OpenAiImageModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }

        var builder = OpenAiImageModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.size().ifPresent(builder::size);
        config.quality().ifPresent(builder::quality);
        config.style().ifPresent(builder::style);
        config.user().ifPresent(builder::user);
        config.responseFormat().ifPresent(builder::responseFormat);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.withPersisting().ifPresent(builder::withPersisting);
        config.persistTo().ifPresent(builder::persistTo);
        config.proxy().ifPresent(builder::proxy);
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiImageModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, OpenAi.OPEN_AI_QUALIFIER));
    }

    private static Optional<OpenAiImageModel> buildModel(OpenAiImageModelConfig.Builder configBuilder,
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
