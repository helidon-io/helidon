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

import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import dev.langchain4j.model.openai.OpenAiLanguageModel;
import dev.langchain4j.model.openai.OpenAiModerationModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

@AiProvider.ModelConfig(value = OpenAiChatModel.class)
@AiProvider.ModelConfig(value = OpenAiStreamingChatModel.class)
@AiProvider.ModelConfig(value = OpenAiModerationModel.class)
@AiProvider.ModelConfig(value = OpenAiImageModel.class)
@AiProvider.ModelConfig(value = OpenAiEmbeddingModel.class)
@AiProvider.ModelConfig(value = OpenAiLanguageModel.class)
interface OpenAiLc4jProvider {
}
