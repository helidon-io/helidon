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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the Cohere embedding model, {@link dev.langchain4j.model.cohere.CohereEmbeddingModel}.
 *
 * @see dev.langchain4j.model.cohere.CohereEmbeddingModel
 */
@Prototype.Configured(CohereEmbeddingModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface CohereEmbeddingModelConfigBlueprint extends CohereCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.cohere.embedding-model";

    /**
     * Gets the input type.
     *
     * @return an {@link java.util.Optional} containing the input type
     */
    @Option.Configured
    Optional<String> inputType();

    /**
     * Gets the maximum number of segments per batch.
     *
     * @return an {@link java.util.Optional} containing the maximum number of segments per batch
     */
    @Option.Configured
    Optional<Integer> maxSegmentsPerBatch();
}
