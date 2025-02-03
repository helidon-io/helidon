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

package io.helidon.integrations.langchain4j;

import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.LazyValue;
import io.helidon.common.config.Config;
import io.helidon.common.types.TypeName;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;

import static io.helidon.integrations.langchain4j.EmbeddingStoreContentRetrieverConfigBlueprint.CONFIG_ROOT;

@Service.Singleton
class EmbeddingStoreContentRetrieverFactory implements Supplier<Optional<EmbeddingStoreContentRetriever>> {
    private static final TypeName STORE_TYPE = TypeName.builder()
            .type(EmbeddingStore.class)
            .addTypeArgument(TypeName.create(TextSegment.class))
            .build();

    private final LazyValue<Optional<EmbeddingStoreContentRetriever>> contentRetriever;

    @Service.Inject
    EmbeddingStoreContentRetrieverFactory(ServiceRegistry registry,
                                          Config config) {
        var retrieverConfig =
                EmbeddingStoreContentRetrieverConfig.create(config.get(CONFIG_ROOT));

        if (retrieverConfig.enabled()) {
            this.contentRetriever = LazyValue.create(() -> Optional.of(buildContentRetriever(registry, retrieverConfig)));
        } else {
            this.contentRetriever = LazyValue.create(Optional.empty());
        }
    }

    @Override
    public Optional<EmbeddingStoreContentRetriever> get() {
        return contentRetriever.get();
    }

    private EmbeddingStoreContentRetriever buildContentRetriever(ServiceRegistry registry,
                                                                 EmbeddingStoreContentRetrieverConfig config) {
        var builder = EmbeddingStoreContentRetriever.builder();
        config.embeddingModel()
                .ifPresent(t -> builder.embeddingModel(RegistryHelper.named(registry, t, EmbeddingModel.class)));
        builder.embeddingStore(RegistryHelper.named(registry,
                                                    config.embeddingStore(),
                                                    STORE_TYPE));
        config.displayName().ifPresent(builder::displayName);
        config.maxResults().ifPresent(builder::maxResults);
        config.minScore().ifPresent(builder::minScore);

        return builder.build();
    }

}
