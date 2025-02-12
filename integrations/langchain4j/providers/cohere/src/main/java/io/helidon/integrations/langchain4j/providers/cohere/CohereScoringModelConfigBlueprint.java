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

import java.net.Proxy;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the Cohere embedding model, {@link dev.langchain4j.model.cohere.CohereScoringModel}.
 *
 * @see dev.langchain4j.model.cohere.CohereScoringModel
 */
@Prototype.Configured(CohereScoringModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface CohereScoringModelConfigBlueprint extends CohereCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.cohere.scoring-model";

    /**
     * Gets the maximum number of retries for failed API requests.
     *
     * @return an {@link java.util.Optional} containing the maximum number of retries
     */
    @Option.Configured
    Optional<Integer> maxRetries();

    /**
     * Proxy to use.
     *
     * @return an {@link java.util.Optional} containing HTTP proxy to use
     */
    Optional<Proxy> proxy();
}
