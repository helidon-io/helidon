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

package io.helidon.integrations.langchain4j;

import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

/**
 * Factory for content retrievers.
 *
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class InMemoryEmbeddingStoreFactory implements Service.ServicesFactory<EmbeddingStore<TextSegment>> {

    private final List<String> embeddingStoreNames;
    private final Config config;

    @Service.Inject
    InMemoryEmbeddingStoreFactory(Config config) {
        this.config = config;
        this.embeddingStoreNames =
                HelidonConstants.modelNames(config, HelidonConstants.ConfigCategory.EMBEDDING_STORE, "helidon");
    }

    /**
     * Create an instance of embedding store from configuration.
     *
     * @param c configuration
     * @return content retriever instance
     * @throws java.lang.IllegalStateException in case the configuration is not enabled
     */
    static Optional<EmbeddingStore<TextSegment>> create(String name, Config c) {
        var mergedConfig = HelidonConstants.create(c, HelidonConstants.ConfigCategory.EMBEDDING_STORE, name);
        var config = InMemoryEmbeddingStoreConfig.create(mergedConfig);

        if (!config.enabled()) {
            return Optional.empty();
        }
        return Optional.of(new InMemoryEmbeddingStore<>());
    }

    @Override
    public List<Service.QualifiedInstance<EmbeddingStore<TextSegment>>> services() {
        return embeddingStoreNames.stream()
                .map(name -> create(name, config)
                        .map(model ->
                                     Service.QualifiedInstance.create(model, Qualifier.createNamed(name)))
                )
                .flatMap(Optional::stream)
                .toList();
    }
}
