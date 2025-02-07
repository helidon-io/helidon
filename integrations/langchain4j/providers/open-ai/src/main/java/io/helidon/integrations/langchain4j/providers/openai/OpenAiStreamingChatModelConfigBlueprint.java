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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import dev.langchain4j.model.Tokenizer;

/**
 * Configuration for the OpenAI streaming chat model, {@link dev.langchain4j.model.openai.OpenAiStreamingChatModel}.
 * Provides methods for setting up and managing properties related to OpenAI API requests.
 *
 * @see dev.langchain4j.model.openai.OpenAiStreamingChatModel
 */
@Prototype.Configured(OpenAiStreamingChatModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OpenAiStreamingChatModelConfigBlueprint extends OpenAiCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.open-ai.streaming-chat-model";

    /**
     * The sampling temperature to use, between 0 and 2.
     * Higher values make the output more random, while lower values make it
     * more focused and deterministic.
     *
     * @return an {@link java.util.Optional} containing the sampling temperature
     */
    @Option.Configured
    Optional<Double> temperature();

    /**
     * The nucleus sampling value, where the model considers the results
     * of the tokens with top_p probability mass.
     *
     * @return an {@link java.util.Optional} containing the nucleus sampling value
     */
    @Option.Configured
    Optional<Double> topP();

    /**
     * The list of sequences where the API will stop generating further
     * tokens.
     *
     * @return the list of stop sequences
     */
    @Option.Configured
    List<String> stop();

    /**
     * The maximum number of tokens to generate in the completion.
     *
     * @return an {@link java.util.Optional} containing the maximum number of tokens
     */
    @Option.Configured
    Optional<Integer> maxTokens();

    /**
     * The maximum number of tokens allowed for the model's response.
     *
     * @return an {@link java.util.Optional} containing the maximum number of completion tokens
     */
    @Option.Configured
    Optional<Integer> maxCompletionTokens();

    /**
     * The presence penalty, between -2.0 and 2.0.
     * Positive values penalize new tokens based on whether they appear in
     * the text so far, encouraging the model to use new words.
     *
     * @return an {@link java.util.Optional} containing the presence penalty
     */
    @Option.Configured
    Optional<Double> presencePenalty();

    /**
     * The frequency penalty, between -2.0 and 2.0.
     * Positive values penalize new tokens based on their existing frequency
     * in the text so far, decreasing the model's likelihood to repeat the
     * same line.
     *
     * @return an {@link java.util.Optional} containing the frequency penalty
     */
    @Option.Configured
    Optional<Double> frequencyPenalty();

    /**
     * LogitBias adjusts the likelihood of specific tokens appearing in a model's response. A map of token IDs to
     * bias values (-100 to 100). Positive values increase the chance of the token, while negative values reduce it, allowing
     * fine control over token preferences in the output.
     *
     * @return a logitBias map
     */
    @Option.Configured
    @Option.Singular
    Map<String, Integer> logitBias();

    /**
     * The format in which the model should return the response.
     *
     * @return an {@link java.util.Optional} containing the response format
     */
    @Option.Configured
    Optional<String> responseFormat();

    /**
     * The seed for the random number generator used by the model.
     *
     * @return an {@link java.util.Optional} containing the seed
     */
    @Option.Configured
    Optional<Integer> seed();

    /**
     * The user ID associated with the API requests.
     *
     * @return an {@link java.util.Optional} containing the user ID
     */
    @Option.Configured
    Optional<String> user();

    /**
     * Whether to enforce strict validation of tools used by the model.
     *
     * @return an {@link java.util.Optional} containing true if strict tools are enforced, false otherwise
     */
    @Option.Configured
    Optional<Boolean> strictTools();

    /**
     * Whether to allow parallel calls to tools.
     *
     * @return an {@link java.util.Optional} containing true if parallel tool calls are allowed, false otherwise
     */
    @Option.Configured
    Optional<Boolean> parallelToolCalls();

    /**
     * Tokenizer to use.
     *
     * @return an {@link java.util.Optional} containing the tokenizer
     */
    Optional<Tokenizer> tokenizer();
}
