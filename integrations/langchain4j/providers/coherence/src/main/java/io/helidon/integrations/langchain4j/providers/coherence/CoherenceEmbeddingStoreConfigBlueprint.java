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
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the Coherence embedding store, {@link dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore}.
 *
 * @see dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore
 */
@Prototype.Configured(CoherenceEmbeddingStoreConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface CoherenceEmbeddingStoreConfigBlueprint {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.coherence.embedding-store";

    /**
     * If set to {@code true},  embedding store will be enabled.
     *
     * @return whether Coherence embedding store is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * The name of the Coherence session to use.
     *
     * @return an {@link java.util.Optional} containing the session name qualifier.
     */
    @Option.Configured
    Optional<String> session();

    /**
     * The name of the Coherence {@link com.tangosol.net.NamedMap} to use to store embeddings.
     *
     * @return an {@link java.util.Optional} containing the NamedMap name.
     */
    @Option.Configured
    Optional<String> name();

    /**
     * The index name to use.
     *
     * @return an {@link java.util.Optional} containing index name.
     */
    @Option.Configured
    Optional<String> index();

    /**
     * Force normalization of embeddings on add and search.
     *
     * @return an {@link java.util.Optional} indicating force normalization.
     */
    @Option.Configured
    Optional<Boolean> normalizeEmbeddings();

    /**
     * The number of dimensions in the embeddings.
     * <p>
     * If an embedding model configures than the model's dimensions
     * will be used instead of this configuration.
     *
     * @return an {@link java.util.Optional} containing number of dimensions in the embeddings.
     */
    @Option.Configured
    Optional<Integer> dimension();
}
