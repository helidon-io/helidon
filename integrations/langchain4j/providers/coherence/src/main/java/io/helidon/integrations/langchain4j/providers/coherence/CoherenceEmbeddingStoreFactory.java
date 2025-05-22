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

package io.helidon.integrations.langchain4j.providers.coherence;

import java.util.List;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import com.oracle.coherence.ai.DocumentChunk;
import com.oracle.coherence.ai.VectorIndexExtractor;
import com.oracle.coherence.ai.hnsw.HnswIndex;
import com.tangosol.util.ValueExtractor;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore;

/**
 * Factory class for creating a configured {@link dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore}.
 *
 * @see dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore
 * @see CoherenceEmbeddingStoreConfig
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class CoherenceEmbeddingStoreFactory implements Service.ServicesFactory<CoherenceEmbeddingStore> {
    private static final System.Logger LOGGER = System.getLogger(CoherenceEmbeddingStoreFactory.class.getName());
    private static final Qualifier COHERENCE_QUALIFIER = Qualifier.createNamed("coherence");

    private final CoherenceEmbeddingStore store;
    private final boolean enabled;

    /**
     * Creates {@link CoherenceEmbeddingStore}.
     */
    @Service.Inject
    CoherenceEmbeddingStoreFactory(ServiceRegistry registry, Config config) {
        var storeConfig = CoherenceEmbeddingStoreConfig.create(config.get(CoherenceEmbeddingStoreConfig.CONFIG_ROOT));
        this.enabled = storeConfig.enabled();

        if (enabled) {
            this.store = buildStore(registry, storeConfig);
        } else {
            this.store = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<CoherenceEmbeddingStore>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(store),
                           Service.QualifiedInstance.create(store, COHERENCE_QUALIFIER));
        }
        return List.of();
    }

    /**
     * Creates and returns an {@link dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore} configured using the provided
     * configuration.
     *
     * @param config the configuration bean
     * @return a configured instance of {@link dev.langchain4j.store.embedding.coherence.CoherenceEmbeddingStore}
     */
    private CoherenceEmbeddingStore buildStore(ServiceRegistry registry, CoherenceEmbeddingStoreConfig config) {
        var builder = CoherenceEmbeddingStore.builder();
        config.session().ifPresent(builder::session);
        config.name().ifPresent(builder::name);
        config.normalizeEmbeddings().ifPresent(builder::normalizeEmbeddings);

        VectorIndexExtractor extractor = null;
        if (config.index().isPresent()) {
            if ("hnsw".equalsIgnoreCase(config.index().get())) {
                EmbeddingModel embeddingModel = registry.get(EmbeddingModel.class);
                Integer dimension = embeddingModel != null ? (Integer) embeddingModel.dimension() : (config.dimension().isPresent() ? config.dimension().get() : null );
                if (dimension != null) {
                    extractor = new HnswIndex<>(ValueExtractor.of(DocumentChunk::vector), dimension);
                } else {
                    LOGGER.log(System.Logger.Level.WARNING,
                               "Cannot create embedding hnsw store index - No dimension name has been specified for the hnsw index.");
                }
            }
        }
        builder.index(extractor);

        return builder.build();
    }

}
