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

@Prototype.Configured(ContentRetrieverConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface ContentRetrieverConfigBlueprint {
    /**
     * The default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.content-retrievers";

    /**
     * If set to {@code false}, component will be disabled even if configured.
     *
     * @return whether the component should be enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    @Option.Configured
    @Option.Default("EMBEDDING_STORE_CONTENT_RETRIEVER")
    @Option.AllowedValues({
            @Option.AllowedValue(value = "embedding-store-content-retriever",
                                 description = "Embedding store backed content retriever"),
            @Option.AllowedValue(value = "web-search-content-retriever",
                                 description = "Web search backed content retriever"),
    })
    Type type();

    /**
     * Embedding store to use in the content retriever.
     *
     * @return an {@link java.util.Optional} default service bean is injected or {@code embedding-model.service-registry.named}
     *         can be used to select a named bean
     */
    @Option.Configured
    String embeddingStore();

    /**
     * Explicit embedding model to use in the content retriever.
     *
     * @return an {@link java.util.Optional} default service bean is injected or {@code embedding-model.service-registry.named}
     *         can be used to select a named bean
     */
    @Option.Configured
    Optional<String> embeddingModel();

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

    enum Type {
        EMBEDDING_STORE_CONTENT_RETRIEVER("embedding-store-content-retriever"),
        WEB_SEARCH_CONTENT_RETRIEVER("web-search-content-retriever");
        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
