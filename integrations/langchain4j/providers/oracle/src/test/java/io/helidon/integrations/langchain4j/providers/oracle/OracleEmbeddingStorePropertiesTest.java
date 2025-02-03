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

import io.helidon.common.config.Config;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.store.embedding.oracle.CreateOption;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class OracleEmbeddingStorePropertiesTest {

    @Test
    void testDefaultRoot() {
        var config = OracleEmbeddingStoreConfig.create(Services.get(Config.class)
                                                               .get(OracleEmbeddingStoreConfig.CONFIG_ROOT));
        assertThat(config, is(notNullValue()));
        assertThat(config.dataSource().isPresent(), is(true));
        assertThat(config.dataSource().get(), is("datasource"));
        assertThat(config.exactSearch().isPresent(), is(true));
        assertThat(config.exactSearch().get(), is(false));
        assertThat(config.vectorIndexCreateOption().isPresent(), is(true));
        assertThat(config.vectorIndexCreateOption().get(), is(CreateOption.CREATE_IF_NOT_EXISTS));
        assertThat(config.embeddingTable().isPresent(), is(true));

        var tableConfig = config.embeddingTable().get();
        assertThat(tableConfig.createOption().isPresent(), is(true));
        assertThat(tableConfig.createOption().get(), is(CreateOption.CREATE_IF_NOT_EXISTS));
        assertThat(tableConfig.embeddingColumn().isPresent(), is(true));
        assertThat(tableConfig.embeddingColumn().get(), is("embeddingColumn"));
        assertThat(tableConfig.idColumn().isPresent(), is(true));
        assertThat(tableConfig.idColumn().get(), is("idColumn"));
        assertThat(tableConfig.name().isPresent(), is(true));
        assertThat(tableConfig.name().get(), is("name"));
        assertThat(tableConfig.metadataColumn().isPresent(), is(true));
        assertThat(tableConfig.metadataColumn().get(), is("metadataColumn"));
        assertThat(tableConfig.textColumn().isPresent(), is(true));
        assertThat(tableConfig.textColumn().get(), is("textColumn"));
    }
}
