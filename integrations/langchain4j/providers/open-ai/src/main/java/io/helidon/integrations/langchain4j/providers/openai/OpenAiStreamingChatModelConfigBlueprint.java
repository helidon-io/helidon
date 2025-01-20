/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the OpenAI streaming chat model, {@link dev.langchain4j.model.openai.OpenAiStreamingChatModel}.
 * Provides methods for setting up and managing properties related to OpenAI API requests.
 *
 * @see dev.langchain4j.model.openai.OpenAiStreamingChatModel
 */
@Prototype.Configured(OpenAiStreamingChatModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OpenAiStreamingChatModelConfigBlueprint {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.open-ai.streaming-chat-model";

    /**
     * If set to {@code false}, OpenAI moderation model will not be available even if configured.
     *
     * @return whether OpenAI model is enabled, defaults to {@code true}
     */
    @Option.DefaultBoolean(true)
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
     * Gets the sampling temperature to use, between 0 and 2.
     * Higher values make the output more random, while lower values make it
     * more focused and deterministic.
     *
     * @return the sampling temperature
     */
    @Option.Configured
    Optional<Double> temperature();

    /**
     * Gets the nucleus sampling value, where the model considers the results
     * of the tokens with top_p probability mass.
     *
     * @return the nucleus sampling value
     */
    @Option.Configured
    Optional<Double> topP();

    /**
     * Gets the list of sequences where the API will stop generating further
     * tokens.
     *
     * @return the list of stop sequences
     */
    @Option.Configured
    List<String> stop();

    /**
     * Gets the maximum number of tokens to generate in the completion.
     *
     * @return the maximum number of tokens
     */
    @Option.Configured
    Optional<Integer> maxTokens();

    /**
     * Gets the maximum number of tokens allowed for the model's response.
     *
     * @return the maximum number of completion tokens
     */
    @Option.Configured
    Optional<Integer> maxCompletionTokens();

    /**
     * Gets the presence penalty, between -2.0 and 2.0.
     * Positive values penalize new tokens based on whether they appear in
     * the text so far, encouraging the model to use new words.
     *
     * @return the presence penalty
     */
    @Option.Configured
    Optional<Double> presencePenalty();

    /**
     * Gets the frequency penalty, between -2.0 and 2.0.
     * Positive values penalize new tokens based on their existing frequency
     * in the text so far, decreasing the model's likelihood to repeat the
     * same line.
     *
     * @return the frequency penalty
     */
    @Option.Configured
    Optional<Double> frequencyPenalty();

    /**
     * Gets logitBias. LogitBias adjusts the likelihood of specific tokens appearing in a model's response. A map of token IDs to
     * bias values (-100 to 100). Positive values increase the chance of the token, while negative values reduce it, allowing
     * fine control over token preferences in the output.
     *
     * @return a logitBias map
     */
    @Option.Configured
    Map<String, Integer> logitBias();

    /**
     * Gets the format in which the model should return the response.
     *
     * @return the response format
     */
    @Option.Configured
    Optional<String> responseFormat();

    /**
     * Gets the seed for the random number generator used by the model.
     *
     * @return the seed
     */
    @Option.Configured
    Optional<Integer> seed();

    /**
     * Gets the user ID associated with the API requests.
     *
     * @return the user ID
     */
    @Option.Configured
    Optional<String> user();

    /**
     * Gets whether to enforce strict validation of tools used by the model.
     *
     * @return true if strict tools are enforced, false otherwise
     */
    @Option.Configured
    Optional<Boolean> strictTools();

    /**
     * Gets whether to allow parallel calls to tools.
     *
     * @return true if parallel tool calls are allowed, false otherwise
     */
    @Option.Configured
    Optional<Boolean> parallelToolCalls();

    /**
     * Gets the timeout setting for API requests.
     *
     * @return the timeout
     */
    @Option.Configured
    Optional<Duration> timeout();

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
     * Gets tokenizer CDI bean name.
     *
     * @return tokenizer CDI bean name or "discovery:auto" if the bean must be discovered automatically
     */
    @Option.Configured
    Optional<String> tokenizer();

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
