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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;

interface CohereCommonConfig {
    /**
     * If set to {@code false} (default), Cohere model will not be available even if configured.
     *
     * @return whether Cohere model is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * The base URL for the Cohere API.
     *
     * @return the base URL
     */
    @Option.Configured
    Optional<String> baseUrl();

    /**
     * The API key used to authenticate requests to the Cohere API.
     *
     * @return an {@link java.util.Optional} containing the API key
     */
    @Option.Configured
    Optional<String> apiKey();

    /**
     * Whether to log API requests.
     *
     * @return an {@link java.util.Optional} containing true if requests should be logged, false otherwise
     */
    @Option.Configured
    Optional<Boolean> logRequests();

    /**
     * Whether to log API responses.
     *
     * @return an {@link java.util.Optional} containing true if responses should be logged, false otherwise
     */
    @Option.Configured
    Optional<Boolean> logResponses();

    /**
     * A map containing custom headers.
     *
     * @return custom headers map
     */
    @Option.Configured
    @Option.Singular
    Map<String, String> customHeaders();

    /**
     * The timeout setting for API requests.
     *
     * @return the timeout setting in {@link java.time.Duration#parse} format
     */
    @Option.Configured
    Optional<Duration> timeout();

    /**
     * The model name to use.
     *
     * @return the model name
     */
    @Option.Configured
    Optional<String> modelName();
}
