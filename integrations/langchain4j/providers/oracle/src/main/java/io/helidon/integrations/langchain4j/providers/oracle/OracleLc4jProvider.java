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

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import javax.sql.DataSource;

import io.helidon.builder.api.Option;
import io.helidon.common.Weighted;
import io.helidon.integrations.langchain4j.AiProvider;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.IVFIndexBuilder;
import dev.langchain4j.store.embedding.oracle.Index;
import dev.langchain4j.store.embedding.oracle.JSONIndexBuilder;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;

@AiProvider.ModelConfig(value = OracleEmbeddingStore.class, skip = {"index.*", "embeddingTable.*"})
interface OracleLc4jProvider {

    /**
     * Default weight used for model factories.
     */
    @AiProvider.DefaultWeight
    double WEIGHT = Weighted.DEFAULT_WEIGHT - 10.0;

    /**
     * Configures a table used to store embeddings, text, and metadata.
     *
     * @return an {@link java.util.Optional} containing the table used to store embeddings if set; otherwise, an empty
     *         {@link java.util.Optional}.
     */
    @Option.Configured
    @AiProvider.NestedConfig(value = EmbeddingTable.class)
    Optional<EmbeddingTableConfig> embeddingTable();

    /**
     * JSONIndex allows configuring a function-based index on one or
     * several keys of the metadata column of the {@code EmbeddingTable}. The function
     * used to index a key is the same as the function used for searching on the store.
     *
     * @return a list of json indexes
     */
    @Option.Configured
    @AiProvider.NestedConfig(value = Index.class,
                             parent = JsonIndexNestedParentBlueprint.class,
                             builderMethod = "jsonIndexBuilder")
    @AiProvider.CustomBuilderMapping
    List<JsonIndexConfig> jsonIndex();

    /**
     * IVFIndex allows configuring an Inverted File Flat (IVF) index
     * on the embedding column of the {@code EmbeddingTable}.
     *
     * @return a list of ivf indexes
     */
    @Option.Configured
    @AiProvider.NestedConfig(value = Index.class,
                             parent = IvfIndexNestedParentBlueprint.class,
                             builderMethod = "ivfIndexBuilder")
    @AiProvider.CustomBuilderMapping
    List<IvfIndexConfig> ivfIndex();

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
    Optional<CreateOption> vectorIndex();

    /**
     * Configures the creation of an index on the embedding column of the {@code EmbeddingTable} used by the
     * embedding store. Depending on which CreateOption is provided, an index may be created.
     * The default createOption is {@link CreateOption#CREATE_NONE}.
     *
     * @return an {@link java.util.Optional} containing the vector index creation option if set; otherwise, an empty
     *         {@link java.util.Optional}
     * @deprecated Use vectorIndex instead
     */
    @Option.Deprecated("vectorIndex")
    Optional<CreateOption> vectorIndexCreateOption();

    /**
     * Configures a data source that connects to an Oracle Database.
     *
     * @return Data source to configure. Not null.
     */
    @Option.Configured
    @Option.RegistryService
    DataSource dataSource();

    /**
     * Customization of Lc4j model builder configuration.
     *
     * @return partially configured Lc4j model builder, to be finished by generated blueprint.
     */
    default OracleEmbeddingStore.Builder configuredBuilder() {
        var modelBuilder = OracleEmbeddingStore.builder();
        var indexes = Stream
                .concat(this.ivfIndex().stream()
                                .map(IvfIndexConfigBlueprint::configuredBuilder)
                                .map(IVFIndexBuilder::build),
                        this.jsonIndex().stream()
                                .map(JsonIndexConfigBlueprint::configuredBuilder)
                                .map(JSONIndexBuilder::build))
                .toList();
        if (!indexes.isEmpty()) {
            modelBuilder.index(indexes.toArray(Index[]::new));
        }
        return modelBuilder;
    }
}
