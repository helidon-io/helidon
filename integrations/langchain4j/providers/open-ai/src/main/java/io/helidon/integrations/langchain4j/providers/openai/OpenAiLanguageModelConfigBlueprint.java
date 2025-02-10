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

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import dev.langchain4j.model.Tokenizer;

/**
 * Configuration for the OpenAI language model, {@link dev.langchain4j.model.openai.OpenAiLanguageModel}.
 * Provides methods for setting up and managing properties related to OpenAI API requests.
 *
 * @see dev.langchain4j.model.openai.OpenAiLanguageModel
 */
@Prototype.Configured(OpenAiLanguageModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OpenAiLanguageModelConfigBlueprint extends OpenAiCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.open-ai.language-model";

    /**
     * The sampling temperature to use, between 0 and 2.
     * Higher values make the output more random, while lower values make it
     * more focused and deterministic.
     *
     * @return the sampling temperature
     */
    @Option.Configured
    Optional<Double> temperature();

    /**
     * Tokenizer to use.
     *
     * @return an {@link java.util.Optional} containing the tokenizer
     */
    Optional<Tokenizer> tokenizer();
}
