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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.cohere.CohereEmbeddingModel;

/**
 * Factory for a configured {@link CohereEmbeddingModel}.
 *
 * @see CohereEmbeddingModel
 * @see io.helidon.integrations.langchain4j.providers.cohere.CohereEmbeddingModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(Cohere.WEIGHT)
public class CohereEmbeddingModelFactory implements Service.ServicesFactory<CohereEmbeddingModel> {
    private final Supplier<Optional<CohereEmbeddingModel>> model;

    CohereEmbeddingModelFactory(Config config) {
        var configBuilder = CohereEmbeddingModelConfig.builder()
                .config(config.get(CohereEmbeddingModelConfigBlueprint.CONFIG_ROOT));

        this.model = () -> buildModel(configBuilder);
    }

    /**
     * Create the Cohere model from its configuration.
     *
     * @param config configuration to use
     * @return a new model instance
     * @throws IllegalStateException in case the configuration is not enabled
     */
    public static CohereEmbeddingModel create(CohereEmbeddingModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }

        var builder = CohereEmbeddingModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.modelName().ifPresent(builder::modelName);
        config.inputType().ifPresent(builder::inputType);
        config.timeout().ifPresent(builder::timeout);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.maxSegmentsPerBatch().ifPresent(builder::maxSegmentsPerBatch);
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<CohereEmbeddingModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, Cohere.COHERE_QUALIFIER));
    }

    private static Optional<CohereEmbeddingModel> buildModel(CohereEmbeddingModelConfig.Builder configBuilder) {
        if (!configBuilder.enabled()) {
            return Optional.empty();
        }

        return Optional.of(create(configBuilder.build()));
    }
}
