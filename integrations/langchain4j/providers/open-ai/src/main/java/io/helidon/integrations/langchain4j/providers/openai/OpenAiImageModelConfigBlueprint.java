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

import java.nio.file.Path;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Configuration for the OpenAI image model, {@link dev.langchain4j.model.openai.OpenAiImageModel}.
 * Provides methods for setting up and managing properties related to OpenAI API requests.
 *
 * @see dev.langchain4j.model.openai.OpenAiImageModel
 */
@Prototype.Configured(OpenAiImageModelConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OpenAiImageModelConfigBlueprint extends OpenAiCommonConfig {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.open-ai.image-model";

    /**
     * The desired size of the generated images.
     *
     * @return the image size.
     */
    @Option.Configured
    Optional<String> size();

    /**
     * The quality of the generated images.
     *
     * @return the image quality.
     */
    @Option.Configured
    Optional<String> quality();

    /**
     * The style of the generated images.
     *
     * @return the image style.
     */
    @Option.Configured
    Optional<String> style();

    /**
     * The unique identifier for the user making the request.
     *
     * @return the user ID.
     */
    @Option.Configured
    Optional<String> user();

    /**
     * The format of the response.
     *
     * @return the response format.
     */
    @Option.Configured
    Optional<String> responseFormat();

    /**
     * The flag to indicate whether to persist the generated images.
     *
     * @return the persist flag.
     */
    @Option.Configured
    Optional<Boolean> withPersisting();

    /**
     * The path or location where the generated images should be persisted.
     *
     * @return the persist path.
     */
    @Option.Configured
    Optional<Path> persistTo();

}
