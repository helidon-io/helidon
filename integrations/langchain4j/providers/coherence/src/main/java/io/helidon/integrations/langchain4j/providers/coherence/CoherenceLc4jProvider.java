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

package io.helidon.integrations.langchain4j.providers.coherence;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.common.Weighted;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore;

@AiProvider.ModelConfig(value = CoherenceEmbeddingStore.class, skip = {"index.*"})
interface CoherenceLc4jProvider {
    /**
     * Default weight used for model factories.
     */
    @AiProvider.DefaultWeight
    double WEIGHT = Weighted.DEFAULT_WEIGHT - 10.0;

    /**
     * The index name to use.
     *
     * @return an {@link java.util.Optional} containing index name.
     */
    @Option.Configured
    @AiProvider.CustomBuilderMapping
    Optional<String> index();

    /**
     * The number of dimensions in the embeddings.
     * <p>
     * If an embedding model is configured than the model's dimensions
     * will be used instead of this configuration.
     *
     * @return an {@link java.util.Optional} containing number of dimensions in the embeddings.
     */
    @Option.Configured
    @AiProvider.CustomBuilderMapping
    Optional<Integer> dimension();

    @Option.Configured
    @Option.RegistryService
    @AiProvider.CustomBuilderMapping
    Optional<EmbeddingModel> embeddingModel();

    /**
     * Customization of Lc4j model builder configuration.
     *
     * @return partially configured Lc4j model builder, to be finished by generated blueprint.
     */
    default CoherenceEmbeddingStore.Builder configuredBuilder() {
        var modelBuilder = CoherenceEmbeddingStore.builder();
        modelBuilder.index(CoherenceFactoryMethods.createVectorIndexExtractor(this.index(), this.dimension(), this.embeddingModel()));
        return modelBuilder;
    }
}
