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

package io.helidon.integrations.langchain4j.providers.ollama;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.ollama.OllamaStreamingChatModel;

/**
 * Factory for a configured {@link dev.langchain4j.model.ollama.OllamaStreamingChatModel}.
 *
 * @see dev.langchain4j.model.ollama.OllamaStreamingChatModel
 * @see io.helidon.integrations.langchain4j.providers.ollama.OllamaStreamingChatModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(Ollama.WEIGHT)
public class OllamaStreamingChatModelFactory implements Service.ServicesFactory<OllamaStreamingChatModel> {
    private final Supplier<Optional<OllamaStreamingChatModel>> model;

    OllamaStreamingChatModelFactory(Config config) {
        var configBuilder = OllamaStreamingChatModelConfig.builder()
                .config(config.get(OllamaStreamingChatModelConfig.CONFIG_ROOT));

        this.model = () -> buildModel(configBuilder);
    }

    /**
     * Create the Ollama model from its configuration.
     *
     * @param config configuration to use
     * @return a new model instance
     * @throws IllegalStateException in case the configuration is not enabled
     */
    public static OllamaStreamingChatModel create(OllamaStreamingChatModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }
        var builder = OllamaStreamingChatModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.modelName().ifPresent(builder::modelName);
        config.temperature().ifPresent(builder::temperature);
        config.topK().ifPresent(builder::topK);
        config.topP().ifPresent(builder::topP);
        config.seed().ifPresent(builder::seed);
        config.repeatPenalty().ifPresent(builder::repeatPenalty);
        config.numPredict().ifPresent(builder::numPredict);
        if (!config.stop().isEmpty()) {
            builder.stop(config.stop());
        }
        config.format().ifPresent(builder::format);
        config.timeout().ifPresent(builder::timeout);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<OllamaStreamingChatModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, Ollama.OLLAMA_QUALIFIER));

    }

    private static Optional<OllamaStreamingChatModel> buildModel(OllamaStreamingChatModelConfig.Builder configBuilder) {
        if (!configBuilder.enabled()) {
            return Optional.empty();
        }

        return Optional.of(create(configBuilder.build()));
    }
}
