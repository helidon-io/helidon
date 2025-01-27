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
     * Gets the embedding store CDI bean name or "discovery:auto" if the bean must be discovered automatically.
     *
     * @return an {@link java.util.Optional} containing the embedding store bean name or "discovery:auto" if the bean must be
     * discovered automatically
     */
    @Option.Configured
    Optional<String> embeddingStore();

    /**
     * Gets the embedding model CDI bean name or "discovery:auto" if the bean must be discovered automatically.
     *
     * @return an {@link java.util.Optional} containing the embedding model bean name or "discovery:auto" if the bean must be
     * discovered automatically
     */
    @Option.Configured
    Optional<String> embeddingModel();

    /**
     * Gets the display name.
     *
     * @return an {@link java.util.Optional} containing the display name if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> displayName();

    /**
     * Gets the maximum number of results.
     *
     * @return an {@link java.util.Optional} containing the maximum results if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<Integer> maxResults();

    /**
     * Gets the minimum score threshold.
     *
     * @return an {@link java.util.Optional} containing the minimum score if set, otherwise an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<Double> minScore();
}
