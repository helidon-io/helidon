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

package io.helidon.integrations.langchain4j.providers.lc4jinprocess;

import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.embedding.onnx.AbstractInProcessEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.OnnxEmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;

/**
 * Service factory for the EmbeddingModel.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class InProcessEmbeddingModelFactory implements Service.ServicesFactory<AbstractInProcessEmbeddingModel> {
    private final Config config;
    private final List<String> modelNames;

    /**
     * Creates a new EmbeddingModelFactory.
     *
     * @param config Configuration for the embedding model(s).
     */
    InProcessEmbeddingModelFactory(Config config) {
        this.config = config;
        this.modelNames = InProcessConstants.modelNames(config,
                                                        AbstractInProcessEmbeddingModel.class,
                                                        InProcessLc4jProvider.PROVIDER_KEY);
    }

    /**
     * Builds a new {@link AbstractInProcessEmbeddingModel} configured for the provided model name.
     * If an executor is configured, the model is created using the configured thread pool; otherwise, the default
     * constructor is used.
     *
     * @param modelName Model name.
     * @param config    Configuration root used to resolve the model configuration.
     * @return a configured model instance, or {@link Optional#empty()} if the model is disabled
     */
    protected static Optional<AbstractInProcessEmbeddingModel> buildModel(String modelName, Config config) {
        var c = InProcessEmbeddingModelConfig.builder()
                .config(InProcessConstants.create(config, AbstractInProcessEmbeddingModel.class, modelName))
                .build();

        if (!c.enabled()) {
            return Optional.empty();
        }

        switch (c.type()) {
        case ALL_MINILM_L6_V2_Q:
            return c.executor()
                    .map(e -> new AllMiniLmL6V2QuantizedEmbeddingModel(e.build().get()))
                    .or(() -> Optional.of(new AllMiniLmL6V2QuantizedEmbeddingModel()))
                    .map(AbstractInProcessEmbeddingModel.class::cast);
        case ALL_MINILM_L6_V2:
            return c.executor()
                    .map(e -> new AllMiniLmL6V2EmbeddingModel(e.build().get()))
                    .or(() -> Optional.of(new AllMiniLmL6V2EmbeddingModel()))
                    .map(AbstractInProcessEmbeddingModel.class::cast);
        case CUSTOM:
            var pathToModel = c.pathToModel()
                    .orElseThrow(() -> new ConfigException("Path to model is required for custom embedding model"));
            var pathToTokenizer = c.pathToTokenizer()
                    .orElseThrow(() -> new ConfigException("Path to tokenizer is required for custom embedding model"));
            var poolingMode = c.poolingMode()
                    .orElseThrow(() -> new ConfigException("Pooling mode is required for custom embedding model"));

            return c.executor()
                    .map(e -> new OnnxEmbeddingModel(pathToModel, pathToTokenizer, poolingMode, e.build().get()))
                    .or(() -> Optional.of(new OnnxEmbeddingModel(pathToModel, pathToTokenizer, poolingMode)))
                    .map(AbstractInProcessEmbeddingModel.class::cast);
        default:
            throw new IllegalStateException("Type is required for in-process embedding model.");
        }
    }

    @Override
    public List<Service.QualifiedInstance<AbstractInProcessEmbeddingModel>> services() {
        return modelNames.stream()
                .map(name -> buildModel(name, config)
                        .map(model -> Service.QualifiedInstance
                                .create(model, Qualifier.createNamed(name))))
                .flatMap(Optional::stream)
                .toList();
    }

}
