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

package io.helidon.integrations.langchain4j.providers.openai;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

@AiProvider.ModelConfig(value = OpenAiChatModel.class)
@AiProvider.ModelConfig(value = OpenAiStreamingChatModel.class)
interface OpenAiLc4jProvider {

    /**
     * For chat models, response format enables JSON mode; for image models, this legacy compatibility option is
     * deprecated and ignored.
     * For newer chat models that support Structured Outputs, use supported-capabilities.
     *
     * @return "json_object" to enable JSON mode on older models like gpt-3.5-turbo or gpt-4
     */
    @Option.Configured
    Optional<String> responseFormat();
}
