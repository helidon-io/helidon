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

package io.helidon.integrations.langchain4j.providers.openai;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the OpenAI moderation model, {@link dev.langchain4j.model.openai.OpenAiModerationModel}.
 * Provides methods for setting up and managing properties related to OpenAI API requests.
 *
 * @see dev.langchain4j.model.openai.OpenAiModerationModel
 */
@Prototype.Configured(OpenAiLanguageModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OpenAiModerationModelConfigBlueprint {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.open-ai.moderation-model";

    /**
     * If set to {@code false} (default), OpenAI moderation model will not be available even if configured.
     *
     * @return whether OpenAI model is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * Gets the base URL for the OpenAI API.
     *
     * @return the base URL
     */
    @Option.Configured
    Optional<String> baseUrl();

    /**
     * Gets the API key used to authenticate requests to the OpenAI API.
     *
     * @return the API key
     */
    @Option.Configured
    Optional<String> apiKey();

    /**
     * Gets the ID of the organization for API requests.
     *
     * @return the organization ID
     */
    @Option.Configured
    Optional<String> organizationId();

    /**
     * Gets the model name to use (e.g., "gpt-3.5-turbo").
     *
     * @return the model name
     */
    @Option.Configured
    Optional<String> modelName();

    /**
     * Gets the timeout setting for API requests.
     *
     * @return the timeout
     */
    @Option.Configured
    Optional<Duration> timeout();

    /**
     * Gets the maximum number of retries for failed API requests.
     *
     * @return the maximum number of retries
     */
    @Option.Configured
    Optional<Integer> maxRetries();

    /**
     * Gets whether to log API requests.
     *
     * @return true if requests should be logged, false otherwise
     */
    @Option.Configured
    Optional<Boolean> logRequests();

    /**
     * Gets whether to log API responses.
     *
     * @return true if responses should be logged, false otherwise
     */
    @Option.Configured
    Optional<Boolean> logResponses();

    /**
     * Gets a map containing custom headers.
     *
     * @return custom headers map
     */
    @Option.Configured
    Map<String, String> customHeaders();

    /**
     * Gets proxy CDI bean name.
     *
     * @return proxy CDI bean name or "discovery:auto" if the bean must be discovered automatically
     */
    @Option.Configured
    Optional<String> proxy();
}
