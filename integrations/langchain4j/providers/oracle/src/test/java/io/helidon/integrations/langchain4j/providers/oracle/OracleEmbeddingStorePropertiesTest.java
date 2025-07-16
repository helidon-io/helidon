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

import io.helidon.common.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.store.embedding.oracle.CreateOption;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testing.Test
class OracleEmbeddingStorePropertiesTest {

    @Test
    void testDefaultRoot() {
        var config = OracleEmbeddingStoreConfig.create(Services.get(Config.class)
                                                               .get(OracleEmbeddingStoreConfig.CONFIG_ROOT));
        assertThat(config, is(notNullValue()));
        assertThat(config.enabled(), is(true));
        assertThat(config.dataSource().toString(), is("customDs"));
        assertThat(config.exactSearch().isPresent(), is(true));
        assertThat(config.exactSearch().get(), is(false));
        assertThat(config.vectorIndex().isPresent(), is(true));
        assertThat(config.vectorIndex().get(), is(CreateOption.CREATE_IF_NOT_EXISTS));

        var tableConfig = config.embeddingTable().get();

        assertThat(tableConfig.embeddingColumn(), is(Optional.of("embeddingColumn")));
        assertThat(tableConfig.idColumn(), is(Optional.of("idColumn")));
        assertThat(tableConfig.name(), is(Optional.of("TEST_EMBEDDING_TABLE_NAME")));
        assertThat(tableConfig.metadataColumn(), is(Optional.of("metadataColumn")));
        assertThat(tableConfig.textColumn(), is(Optional.of("textColumn")));

        List<JsonIndexConfig> jsonIndexes = config.jsonIndex();
        assertThat(jsonIndexes.size(), is(2));
        assertThat(jsonIndexes.stream().map(JsonIndexConfigBlueprint::name).flatMap(Optional::stream).toList(),
                   Matchers.containsInAnyOrder("jsonIndexName", "jsonIndexName2"));

        List<IvfIndexConfig> ivfIndexes = config.ivfIndex();
        assertThat(ivfIndexes.size(), is(2));
        assertThat(ivfIndexes.stream().map(IvfIndexConfigBlueprint::name).flatMap(Optional::stream).toList(),
                   Matchers.containsInAnyOrder("ivfIndexName", "ivfIndexName2"));
        assertThat(ivfIndexes.stream().map(IvfIndexConfigBlueprint::createOption).flatMap(Optional::stream).toList(),
                   Matchers.containsInAnyOrder(CreateOption.CREATE_NONE, CreateOption.CREATE_IF_NOT_EXISTS));

        var embeddingStore = config.configuredBuilder().build();
        assertThat(assertThrows(Exception.class,
                                () -> embeddingStore.add(new Embedding(new float[] {3, 3}))).getCause().getMessage(),
                   is("INSERT INTO TEST_EMBEDDING_TABLE_NAME(idColumn, embeddingColumn) VALUES (?, ?)"));
        assertThat(assertThrows(Exception.class,
                                embeddingStore::removeAll).getCause().getMessage(),
                   is("TRUNCATE TABLE TEST_EMBEDDING_TABLE_NAME"));
    }
}
