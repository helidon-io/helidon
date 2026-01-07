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

package io.helidon.integrations.langchain4j.providers.gemini;

import java.util.List;

import io.helidon.builder.api.Option;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.googleai.GeminiSafetySetting;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;

@AiProvider.ModelConfig(GoogleAiGeminiChatModel.class)
@AiProvider.ModelConfig(GoogleAiGeminiStreamingChatModel.class)
interface GoogleGeminiLc4jProvider {
    /**
     * Safety setting, affecting the safety-blocking behavior. Passing a safety setting for a category changes the
     * allowed probability that content is blocked
     *
     * @return List of harm category vs threshold settings
     */
    @Option.Configured
    @Option.Singular
    List<GeminiSafetySetting> safetySettings();
}
