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

import io.helidon.builder.api.Prototype;

/**
 * Configuration class for the Ollama embedding model, {@link dev.langchain4j.model.ollama.OllamaEmbeddingModel}.
 *
 * @see dev.langchain4j.model.ollama.OllamaEmbeddingModel
 */
@Prototype.Configured(OllamaEmbeddingModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
// we want a specific builder generated even if only uses common methods
@SuppressWarnings("checkstyle:InterfaceIsType")
interface OllamaEmbeddingModelConfigBlueprint extends OllamaCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.ollama.embedding-model";
}
