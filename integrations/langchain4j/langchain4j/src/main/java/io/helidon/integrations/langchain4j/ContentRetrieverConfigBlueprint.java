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

package io.helidon.integrations.langchain4j;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for LangChain4j {@code ContentRetriever} components.
 * <p>
 * Instances of this configuration are created from the config and define
 * how a content retriever is selected and initialized (for example, embedding-store backed retrievers).
 */
@Prototype.Configured
@Prototype.Blueprint
interface ContentRetrieverConfigBlueprint {

    /**
     * If set to {@code false}, component will be disabled even if configured.
     *
     * @return whether the component should be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Type of content retriever to create.
     *
     * @return the content retriever type
     */
    @Option.Configured
    @Option.Default("EMBEDDING_STORE_CONTENT_RETRIEVER")
    ContentRetrieverType type();

    /**
     * Embedding store to use in the content retriever.
     * <p>
     * The value identifies a named service that provides embedding store
     * implementation used to retrieve relevant content.
     *
     * @return the embedding store service name
     */
    @Option.Configured
    String embeddingStore();

    /**
     * Explicit embedding model to use in the content retriever.
     * <p>
     * If empty, the default embedding model is used (as resolved by the service registry).
     * If set, the value identifies a named service that provides embedding model bean.
     *
     * @return embedding model reference if configured
     */
    @Option.Configured
    Optional<String> embeddingModel();

    /**
     * Display name for this content retriever configuration.
     *
     * @return the display name if configured
     */
    @Option.Configured
    Optional<String> displayName();

    /**
     * Maximum number of results to return from the retriever.
     * <p>
     * If empty, the retriever implementation default is used.
     *
     * @return maximum results if configured
     */
    @Option.Configured
    Optional<Integer> maxResults();

    /**
     * Minimum score threshold for retrieved results.
     * <p>
     * If empty, the retriever implementation default is used.
     *
     * @return minimum score if configured
     */
    @Option.Configured
    Optional<Double> minScore();
}
