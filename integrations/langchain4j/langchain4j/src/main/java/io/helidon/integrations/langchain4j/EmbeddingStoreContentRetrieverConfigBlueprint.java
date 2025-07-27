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

package io.helidon.integrations.langchain4j;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Configuration class for {@link EmbeddingStoreContentRetrieverConfigBlueprint}.
 *
 * @see dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever
 */
@Prototype.Configured(EmbeddingStoreContentRetrieverConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface EmbeddingStoreContentRetrieverConfigBlueprint {
    /**
     * The default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.rag.embedding-store-content-retriever";

    /**
     * If set to {@code false}, embedding store content retriever will be disabled even if configured.
     *
     * @return whether the content retriever should be enabled
     */
    @Option.Configured
    boolean enabled();

    /**
     * Embedding store to use in the content retriever.
     *
     * @return an {@link java.util.Optional} default service bean is injected or {@code embedding-model.service-registry.named}
     * can be used to select a named bean
     */
    @Option.Configured
    @Option.RegistryService
    EmbeddingStore<TextSegment> embeddingStore();

    /**
     * Explicit embedding model to use in the content retriever.
     *
     * @return an {@link java.util.Optional} default service bean is injected or {@code embedding-model.service-registry.named}
     * can be used to select a named bean
     */
    @Option.Configured
    @Option.RegistryService
    Optional<EmbeddingModel> embeddingModel();

    /**
     * The display name.
     *
     * @return an {@link java.util.Optional} containing the display name if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> displayName();

    /**
     * The maximum number of results.
     *
     * @return an {@link java.util.Optional} containing the maximum results if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<Integer> maxResults();

    /**
     * The minimum score threshold.
     *
     * @return an {@link java.util.Optional} containing the minimum score if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<Double> minScore();
}
