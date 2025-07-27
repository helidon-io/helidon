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
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;

import static io.helidon.integrations.langchain4j.EmbeddingStoreContentRetrieverConfigBlueprint.CONFIG_ROOT;

/**
 * Factory for embedding store content retrievers.
 *
 * @see #create(EmbeddingStoreContentRetrieverConfig)
 */
@Service.Singleton
@Weight(Weighted.DEFAULT_WEIGHT - 10)
public class EmbeddingStoreContentRetrieverFactory implements Supplier<Optional<EmbeddingStoreContentRetriever>> {

    private final LazyValue<Optional<EmbeddingStoreContentRetriever>> contentRetriever;

    @Service.Inject
    EmbeddingStoreContentRetrieverFactory(Config config) {
        var configBuilder =
                EmbeddingStoreContentRetrieverConfig.builder().config(config.get(CONFIG_ROOT));

        if (configBuilder.enabled()) {
            this.contentRetriever = LazyValue.create(() -> {
                if (!configBuilder.enabled()) {
                    return Optional.empty();
                }
                return Optional.of(create(configBuilder.build()));
            });
        } else {
            this.contentRetriever = LazyValue.create(Optional.empty());
        }
    }

    /**
     * Create an instance of embedding store content retriever from configuration.
     *
     * @param config configuration of the content retriever
     * @return content retriever instance
     * @throws java.lang.IllegalStateException in case the configuration is not enabled
     */
    public static EmbeddingStoreContentRetriever create(EmbeddingStoreContentRetrieverConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a retriever when the configuration is disabled.");
        }

        // Langchain4j builder
        var builder = EmbeddingStoreContentRetriever.builder();
        config.embeddingModel()
                .ifPresent(builder::embeddingModel);
        builder.embeddingStore(config.embeddingStore());
        config.displayName().ifPresent(builder::displayName);
        config.maxResults().ifPresent(builder::maxResults);
        config.minScore().ifPresent(builder::minScore);

        return builder.build();
    }

    @Override
    public Optional<EmbeddingStoreContentRetriever> get() {
        return contentRetriever.get();
    }

}
