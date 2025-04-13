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

package io.helidon.integrations.langchain4j.providers.jlama;

import io.helidon.builder.api.Option;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.model.jlama.JlamaChatModel;
import dev.langchain4j.model.jlama.JlamaEmbeddingModel;
import dev.langchain4j.model.jlama.JlamaLanguageModel;
import dev.langchain4j.model.jlama.JlamaStreamingChatModel;

@AiProvider.ModelConfig(JlamaChatModel.class)
@AiProvider.ModelConfig(JlamaStreamingChatModel.class)
@AiProvider.ModelConfig(JlamaLanguageModel.class)
@AiProvider.ModelConfig(JlamaEmbeddingModel.class)
interface JlamaLc4jProvider {

    /**
     * Configure the model name.
     *
     * @return model name
     */
    @Option.Configured
    String modelName();
}
