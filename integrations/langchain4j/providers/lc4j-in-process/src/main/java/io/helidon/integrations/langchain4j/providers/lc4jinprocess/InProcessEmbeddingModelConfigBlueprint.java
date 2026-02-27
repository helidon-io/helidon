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

package io.helidon.integrations.langchain4j.providers.lc4jinprocess;

import java.nio.file.Path;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.ThreadPoolConfig;

import dev.langchain4j.model.embedding.onnx.PoolingMode;

/**
 * Configuration blueprint for LangChain4j in-process models.
 */
@Prototype.Blueprint
@Prototype.Configured(InProcessLc4jProvider.CONFIG_ROOT)
interface InProcessEmbeddingModelConfigBlueprint {

    /**
     * Whether the embedding model is enabled.
     * If set to {@code false}, the model will not be available even if configured.
     *
     * @return whether the embedding model is enabled, defaults to {@code true}
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Executor configuration used by the embedding model.
     *
     * @return optional executor configuration
     */
    @Option.Configured
    Optional<ThreadPoolConfig> executor();

    /**
     * Which in-process ONNX model variant should be used.
     *
     * @return in-process ONNX model provided type
     */
    @Option.Configured
    InProcessModelType type();

    /**
     * The path to the modelPath file (e.g., "/path/to/model.onnx").
     *
     * @return an {@link Optional} containing the configured model path, or an empty {@code Optional} if not set
     */
    @Option.Configured
    Optional<Path> pathToModel();

    /**
     * The path to the tokenizer file (e.g., "/path/to/tokenizer.json").
     *
     * @return an {@link Optional} containing the configured tokenizer path, or an empty {@code Optional} if not set
     */
    @Option.Configured
    Optional<Path> pathToTokenizer();

    /**
     * The pooling model to use. Can be found in the ".../1_Pooling/config.json" file on HuggingFace.
     * Here is an <a href="https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/blob/main/1_Pooling/config.json">
     * example</a>.
     * {@code "pooling_mode_mean_tokens": true} means that {@link PoolingMode#MEAN} should be used.
     *
     * @return an {@link Optional} containing the configured {@link PoolingMode}, or an empty {@code Optional} if no
     *         pooling mode is explicitly configured
     */
    @Option.Configured
    Optional<PoolingMode> poolingMode();
}
