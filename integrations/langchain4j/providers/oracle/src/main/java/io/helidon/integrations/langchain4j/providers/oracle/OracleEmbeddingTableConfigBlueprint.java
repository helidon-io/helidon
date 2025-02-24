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

package io.helidon.integrations.langchain4j.providers.oracle;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Configuration for the Oracle embedding store, {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore}.
 *
 * @see dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore
 */
@Prototype.Configured(OracleEmbeddingTableConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OracleEmbeddingTableConfigBlueprint {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.oracle.embedding-store.embedding-table";

    /**
     * The create option, which defines the behavior when creating the embedding table.
     *
     * @return an {@link java.util.Optional} containing the create option if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<CreateOption> createOption();

    /**
     * The name of the embedding table.
     *
     * @return an {@link java.util.Optional} containing the table name if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> name();

    /**
     * The name of the ID column in the embedding table.
     *
     * @return an {@link java.util.Optional} containing the ID column name if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> idColumn();

    /**
     * The name of the embedding column in the embedding table.
     *
     * @return an {@link java.util.Optional} containing the embedding column name if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> embeddingColumn();

    /**
     * The name of the text column in the embedding table.
     *
     * @return an {@link java.util.Optional} containing the text column name if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> textColumn();

    /**
     * The name of the metadata column in the embedding table.
     *
     * @return an {@link java.util.Optional} containing the metadata column name if set; otherwise, an empty {@link java.util.Optional}
     */
    @Option.Configured
    Optional<String> metadataColumn();
}
