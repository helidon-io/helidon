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

package io.helidon.integrations.langchain4j.providers.ollama;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;

interface OllamaCommonConfig {
    /**
     * If set to {@code false} (default), Ollama model will not be available even if configured.
     *
     * @return whether Ollama model is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * The base URL for the Ollama API.
     *
     * @return the base URL
     */
    @Option.Configured
    Optional<String> baseUrl();

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
     * The maximum number of retries for failed API requests.
     *
     * @return an {@link java.util.Optional} containing the maximum number of retries
     */
    @Option.Configured
    Optional<Integer> maxRetries();

    /**
     * T name of the Ollama model to use. This parameter determines which pre-trained model will process the input
     * prompt and produce the output.
     *
     * <p>Examples of valid model names:
     * <ul>
     *   <li>{@code "llama-2"}: Utilizes the LLaMA 2 model.</li>
     *   <li>{@code "alpaca"}: Uses a fine-tuned LLaMA model for conversational tasks.</li>
     *   <li>{@code "custom-model"}: A user-defined model trained for specific use cases.</li>
     * </ul>
     *
     * @return an {@link java.util.Optional} containing the model name
     */
    @Option.Configured
    Optional<String> modelName();
}
