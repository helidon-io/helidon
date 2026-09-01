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

package io.helidon.integrations.langchain4j.providers.cohere.it;

import io.helidon.integrations.langchain4j.providers.cohere.CohereEmbeddingModelConfig;
import io.helidon.integrations.langchain4j.providers.cohere.CohereScoringModelConfig;

import dev.langchain4j.http.client.HttpClientBuilder;

/**
 * Compiles against Cohere's exported configuration API using only the provider module.
 */
public final class Main {
    private Main() {
    }

    /**
     * Reads both generated public HTTP client properties.
     *
     * @param embeddingConfig embedding model configuration
     * @param scoringConfig scoring model configuration
     * @return HTTP client builder exposed by either configuration
     */
    public static HttpClientBuilder httpClientBuilder(CohereEmbeddingModelConfig embeddingConfig,
                                                      CohereScoringModelConfig scoringConfig) {
        return embeddingConfig.httpClientBuilder()
                .or(() -> scoringConfig.httpClientBuilder())
                .orElseThrow();
    }
}
