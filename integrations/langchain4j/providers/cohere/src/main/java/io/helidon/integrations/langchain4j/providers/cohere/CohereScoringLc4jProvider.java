/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

import java.net.Proxy;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.common.Weighted;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.cohere.CohereScoringModel;

@AiProvider.ModelConfig(value = CohereScoringModel.class,
                        weight = Weighted.DEFAULT_WEIGHT - 20,
                        providerKey = "cohere",
                        skip = {"proxy\\(java\\.net\\.Proxy\\)",
                                "httpClientBuilder\\(dev\\.langchain4j\\.http\\.client\\.HttpClientBuilder\\)"})
interface CohereScoringLc4jProvider {

    /**
     * HTTP client builder to use.
     *
     * @return an {@link Optional} containing HTTP client builder to use
     */
    @Option.Configured
    @Option.RegistryService
    @Option.Decorator(CohereScoringConfigSupport.HttpClientBuilderDecorator.class)
    @AiProvider.CustomBuilderMapping
    Optional<HttpClientBuilder> httpClientBuilder();

    /**
     * Proxy to use.
     *
     * @return an {@link java.util.Optional} containing HTTP proxy to use
     */
    @Option.Configured
    @Option.RegistryService
    @Option.Deprecated("httpClientBuilder")
    @Option.Decorator(CohereScoringConfigSupport.ProxyDecorator.class)
    @AiProvider.CustomBuilderMapping
    Optional<Proxy> proxy();

    /**
     * Customizes the model builder to preserve the legacy proxy configuration through the new LangChain4j HTTP client
     * abstraction.
     *
     * @return partially configured LangChain4j model builder
     */
    default CohereScoringModel.CohereScoringModelBuilder configuredBuilder() {
        var modelBuilder = CohereScoringModel.builder();
        httpClientBuilder()
                .or(() -> proxy().map(CohereHttpClientSupport::create))
                .ifPresent(modelBuilder::httpClientBuilder);
        return modelBuilder;
    }
}
