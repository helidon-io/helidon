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
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Factory for content retrievers.
 *
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class ContentRetrieverFactory implements Service.ServicesFactory<ContentRetriever> {

    private final List<String> contentRetrieverNames;
    private final ServiceRegistry registry;
    private final Config config;

    @Service.Inject
    ContentRetrieverFactory(ServiceRegistry registry, Config config) {
        this.registry = registry;
        this.config = config;
        this.contentRetrieverNames =
                HelidonConstants.modelNames(config, HelidonConstants.ConfigCategory.CONTENT_RETRIEVER, "helidon");
    }

    private Optional<EmbeddingStoreContentRetriever> create(String name) {
        var mergedConfig = HelidonConstants.create(config, HelidonConstants.ConfigCategory.CONTENT_RETRIEVER, name);
        var c = ContentRetrieverConfig.create(mergedConfig);

        if (!c.enabled()) {
            return Optional.empty();
        }

        // Langchain4j builder
        var builder = EmbeddingStoreContentRetriever.builder();
        builder.embeddingStore(registry.getNamed(TypeName.builder(TypeName.create(EmbeddingStore.class))
                                                         .addTypeArgument(TypeName.create(TextSegment.class))
                                                         .build(), c.embeddingStore()));
        c.embeddingModel()
                .flatMap(modelName -> registry.firstNamed(EmbeddingModel.class, modelName))
                .ifPresent(builder::embeddingModel);
        c.displayName().ifPresent(builder::displayName);
        c.maxResults().ifPresent(builder::maxResults);
        c.minScore().ifPresent(builder::minScore);

        return Optional.of(builder.build());
    }

    @Override
    public List<Service.QualifiedInstance<ContentRetriever>> services() {
        return contentRetrieverNames.stream()
                .map(name -> create(name)
                        .map(ContentRetriever.class::cast)
                        .map(model -> Service.QualifiedInstance.create(model, Qualifier.createNamed(name)))
                )
                .flatMap(Optional::stream)
                .toList();
    }
}
