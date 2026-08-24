/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import dev.langchain4j.model.openai.OpenAiImageModel;

@AiProvider.ConfigKey("open-ai")
@AiProvider.ModelConfig(value = OpenAiImageModel.class)
interface OpenAiImageLc4jProvider {

    /**
     * Legacy image style option retained for configuration compatibility; this option is ignored.
     *
     * @return configured image style
     * @deprecated this option is no longer supported by LangChain4j and is ignored
     */
    @Deprecated
    @Option.Configured
    @AiProvider.CustomBuilderMapping
    Optional<String> style();

    /**
     * Legacy image response format option retained for configuration compatibility; this option is ignored.
     *
     * @return configured image response format
     * @deprecated this option is no longer supported by LangChain4j and is ignored
     */
    @Deprecated
    @Option.Configured
    @AiProvider.CustomBuilderMapping
    Optional<String> responseFormat();
}
