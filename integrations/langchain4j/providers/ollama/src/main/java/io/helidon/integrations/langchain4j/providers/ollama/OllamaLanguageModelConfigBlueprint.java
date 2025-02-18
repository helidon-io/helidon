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

import java.util.List;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration class for the Ollama language model, {@link dev.langchain4j.model.ollama.OllamaLanguageModel}.
 *
 * @see dev.langchain4j.model.ollama.OllamaLanguageModel
 */
@Prototype.Configured(OllamaLanguageModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OllamaLanguageModelConfigBlueprint extends OllamaCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.ollama.language-model";

    /**
     * T sampling temperature to use, between 0 and 2.
     * Higher values make the output more random, while lower values make it
     * more focused and deterministic.
     *
     * @return an {@link java.util.Optional} containing the sampling temperature
     */
    @Option.Configured
    Optional<Double> temperature();

    /**
     * T maximum number of top-probability tokens to consider when generating text.
     * Limits the token pool to the {@code topK} highest-probability tokens, controlling
     * the balance between deterministic and diverse outputs.
     *
     * <p>A smaller {@code topK} (e.g., 1) results in deterministic output, while a larger
     * value (e.g., 50) allows for more variability and creativity.
     *
     * @return an {@link java.util.Optional} containing the topK value
     */
    @Option.Configured
    Optional<Integer> topK();

    /**
     * T nucleus sampling value, where the model considers the results
     * of the tokens with top_p probability mass.
     *
     * @return an {@link java.util.Optional} containing the nucleus sampling value
     */
    @Option.Configured
    Optional<Double> topP();

    /**
     * T penalty applied to repeated tokens during text generation.
     * Higher values discourage the model from generating the same token
     * multiple times, promoting more varied and natural output.
     *
     * <p>A value of {@code 1.0} applies no penalty (default behavior), while
     * values greater than {@code 1.0} reduce the likelihood of repetition.
     * Excessively high values may overly penalize common phrases, leading to
     * unnatural results.
     *
     * @return an {@link java.util.Optional} containing the repeat penalty
     */
    @Option.Configured
    Optional<Double> repeatPenalty();

    /**
     * T seed for the random number generator used by the model.
     *
     * @return an {@link java.util.Optional} containing the seed
     */
    @Option.Configured
    Optional<Integer> seed();

    /**
     * T number of tokens to generate during text prediction. This parameter
     * determines the length of the output generated by the model.
     *
     * @return an {@link java.util.Optional} containing the numPredicts value
     */
    @Option.Configured
    Optional<Integer> numPredict();

    /**
     * T list of sequences where the API will stop generating further
     * tokens.
     *
     * @return the list of stop sequences
     */
    @Option.Configured
    List<String> stop();

    /**
     * T format of the generated output. This parameter specifies the structure
     * or style of the text produced by the model, such as plain text, JSON, or a custom format.
     *
     * <p>Common examples:
     * <ul>
     *   <li>{@code "plain"}: Generates unstructured plain text.</li>
     *   <li>{@code "json"}: Produces output formatted as a JSON object.</li>
     *   <li>Custom values may be supported depending on the model's capabilities.</li>
     * </ul>
     *
     * @return an {@link java.util.Optional} containing the response format
     */
    @Option.Configured
    Optional<String> format();
}
