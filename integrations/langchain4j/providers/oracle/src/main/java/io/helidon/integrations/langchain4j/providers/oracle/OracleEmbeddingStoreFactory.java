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

import javax.sql.DataSource;

import io.helidon.common.config.Config;
import io.helidon.integrations.langchain4j.RegistryHelper;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.store.embedding.oracle.EmbeddingTable;
import dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore;

/**
 * Factory class for creating a configured {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore}.
 *
 * @see dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore
 * @see OracleEmbeddingStoreConfig
 */
@Service.Singleton
@Service.Named("*")
class OracleEmbeddingStoreFactory implements Service.ServicesFactory<OracleEmbeddingStore> {
    private static final Qualifier ORACLE_QUALIFIER = Qualifier.createNamed("oracle");

    private final OracleEmbeddingStore store;
    private final boolean enabled;

    /**
     * Creates {@link OracleEmbeddingStore}.
     */
    @Service.Inject
    OracleEmbeddingStoreFactory(ServiceRegistry registry, Config config) {
        var storeConfig = OracleEmbeddingStoreConfig.create(config.get(OracleEmbeddingStoreConfig.CONFIG_ROOT));

        this.enabled = storeConfig.enabled();

        if (enabled) {
            this.store = buildStore(registry, storeConfig);
        } else {
            this.store = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<OracleEmbeddingStore>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(store),
                           Service.QualifiedInstance.create(store, ORACLE_QUALIFIER));
        }
        return List.of();
    }

    /**
     * Creates and returns an {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore} configured using the provided
     * configuration.
     *
     * @param config the configuration bean
     * @return a configured instance of {@link dev.langchain4j.store.embedding.oracle.OracleEmbeddingStore}
     */
    private OracleEmbeddingStore buildStore(ServiceRegistry registry, OracleEmbeddingStoreConfig config) {
        var builder = OracleEmbeddingStore.builder();
        config.dataSource().ifPresent(bn -> builder.dataSource(RegistryHelper.named(registry, bn, DataSource.class)));
        config.embeddingTable().ifPresent(etc -> builder.embeddingTable(creatembeddingTable(etc)));
        config.exactSearch().ifPresent(builder::exactSearch);
        config.vectorIndexCreateOption().ifPresent(builder::vectorIndex);

        return builder.build();
    }

    /**
     * Creates and returns an {@link dev.langchain4j.store.embedding.oracle.EmbeddingTable} configured using the provided
     * configuration.
     *
     * @param config the configuration bean
     * @return a configured instance of {@link dev.langchain4j.store.embedding.oracle.EmbeddingTable}
     */
    private EmbeddingTable creatembeddingTable(OracleEmbeddingTableConfig config) {
        var builder = EmbeddingTable.builder();
        config.createOption().ifPresent(builder::createOption);
        config.name().ifPresent(builder::name);
        config.idColumn().ifPresent(builder::idColumn);
        config.embeddingColumn().ifPresent(builder::embeddingColumn);
        config.textColumn().ifPresent(builder::textColumn);
        config.metadataColumn().ifPresent(builder::metadataColumn);

        return builder.build();
    }
}
