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
import io.helidon.service.registry.Service;

import dev.langchain4j.store.embedding.oracle.CreateOption;

/**
 * Configuration for the Oracle embedding store, {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore}.
 *
 * @see dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore
 */
@Prototype.Configured(OracleEmbeddingStoreConfigBlueprint.CONFIG_ROOT)
@Prototype.Blueprint
interface OracleEmbeddingStoreConfigBlueprint {
    /**
     * Default configuration prefix.
     */
    String CONFIG_ROOT = "langchain4j.oracle.embedding-store";

    /**
     * If set to {@code true}, Oracle embedding store will be enabled.
     *
     * @return whether Oracle embedding store is enabled, defaults to {@code false}
     */
    @Option.Configured
    boolean enabled();

    /**
     * The data source name used for connecting to the Oracle embedding store.
     *
     * @return an {@link java.util.Optional} containing the datasource name qualifier
     */
    @Option.Configured
    @Option.Default(Service.Named.DEFAULT_NAME)
    Optional<String> dataSource();

    /**
     * Properties of the embedding table associated with the Oracle embedding store.
     *
     * @return an {@link java.util.Optional} containing the {@link OracleEmbeddingTableConfig} representing table properties
     */
    @Option.Configured
    Optional<OracleEmbeddingTableConfig> embeddingTable();

    /**
     * The exact search option, which specifies whether exact matching is used in searches.
     *
     * @return an {@link java.util.Optional} containing the exact search option if set; otherwise, an empty
     *         {@link java.util.Optional}
     */
    @Option.Configured
    Optional<Boolean> exactSearch();

    /**
     * The vector index creation option, which defines behavior when creating the vector index.
     *
     * @return an {@link java.util.Optional} containing the vector index creation option if set; otherwise, an empty
     *         {@link java.util.Optional}
     */
    @Option.Configured
    @Option.AllowedValues({
            @Option.AllowedValue(value = "CREATE_NONE", description = "No attempt is made to create the schema object."),
            @Option.AllowedValue(value = "CREATE_IF_NOT_EXISTS", description = "An existing schema object is reused, otherwise "
                    + "it is created."),
            @Option.AllowedValue(value = "CREATE_OR_REPLACE", description = "An existing schema object is dropped and replaced "
                    + "with a new one.")
    })
    Optional<CreateOption> vectorIndexCreateOption();
}
