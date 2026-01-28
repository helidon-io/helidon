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

import java.util.Collection;
import java.util.List;

import io.helidon.config.Config;
import io.helidon.config.MergedConfig;

import dev.langchain4j.store.embedding.EmbeddingStore;

/**
 * Utility class for handling LangChain4j configuration, including model listing,
 * provider merging, and kind resolution.
 */
public final class ConfigUtils {

    /**
     * Root configuration key for LangChain4j.
     */
    public static final String LC4J_KEY = "langchain4j";

    /**
     * Configuration key for model definitions.
     */
    public static final String MODELS_KEY = LC4J_KEY + ".models";
    /**
     * Configuration key for embedding store definitions.
     */
    public static final String EMB_STORES_KEY = LC4J_KEY + ".embedding-stores";
    /**
     * Configuration key for content retrievers definitions.
     */
    public static final String CONTENT_RETRIEVERS_KEY = LC4J_KEY + ".content-retrievers";
    /**
     * Configuration key for provider definitions.
     */
    public static final String PROVIDERS_KEY = LC4J_KEY + ".providers";

    private ConfigUtils() {
    }

    /**
     * Returns the names of models of the given {@code kind} that are configured to use
     * the specified {@code providerKey}.
     *
     * @param config      the root configuration
     * @param kind        the kind of model (e.g., {@link Kind#MODEL} or {@link Kind#EMBEDDING_STORE})
     * @param providerKey the provider name to filter by
     * @return list of model names
     */
    public static List<String> modelNames(Config config, Kind kind, String providerKey) {
        return config.get(kind.key)
                .asNodeList()
                .stream()
                .flatMap(Collection::stream)
                .filter(c -> c.get("provider").asString().filter(providerKey::equals).isPresent())
                .map(Config::key)
                .map(Config.Key::name)
                .toList();
    }

    /**
     * Variant of {@link #modelNames(Config, Kind, String)} that accepts a model class
     * and resolves the corresponding {@link Kind}.
     *
     * @param config      the root configuration
     * @param kindType    the model class (e.g., {@code dev.langchain4j.model.chat.ChatLanguageModel})
     * @param providerKey the provider name to filter by
     * @return list of model names
     */
    public static List<String> modelNames(Config config, Class<?> kindType, String providerKey) {
        return modelNames(config, resolve(kindType), providerKey);
    }

    /**
     * Creates a merged configuration for a model identified by {@code name},
     * combining model-specific settings with its provider's defaults.
     *
     * @param root     the root configuration
     * @param kindType the model class to resolve the {@link Kind}
     * @param name     the name of the model
     * @return merged configuration
     */
    public static Config create(Config root, Class<?> kindType, String name) {
        return create(root, resolve(kindType), name);
    }

    /**
     * Merges model and provider configurations, giving precedence to model-specific
     * settings over those defined in the provider.
     *
     * @param root      the root configuration
     * @param kind      the {@link Kind} of the configuration (model or embedding store)
     * @param modelName the name of the model to create configuration for
     * @return merged configuration
     */
    public static Config create(Config root, Kind kind, String modelName) {
        Config modelConfig = root.get(kind.key).get(modelName);
        Config providerConfig = modelConfig.get("provider").asString()
                .map(providerName -> root.get(PROVIDERS_KEY).get(providerName))
                .orElse(Config.empty());

        return MergedConfig.create(modelConfig, providerConfig);
    }

    /**
     * Resolves the {@link Kind} based on the provided class. If the class implements
     * {@link dev.langchain4j.store.embedding.EmbeddingStore}, {@link Kind#EMBEDDING_STORE}
     * is returned; otherwise {@link Kind#MODEL} is used.
     *
     * @param clazz class to resolve
     * @return corresponding {@link Kind}
     */
    private static Kind resolve(Class<?> clazz) {
        if (EmbeddingStore.class.isAssignableFrom(clazz)) {
            return Kind.EMBEDDING_STORE;
        }
        return Kind.MODEL;
    }

    /**
     * Represents the two supported configuration categories: model definitions
     * and embedding store definitions.
     */
    public enum Kind {
        /**
         * Configuration category for model definitions.
         */
        MODEL(MODELS_KEY),

        /**
         * Configuration category for embedding store definitions.
         */
        EMBEDDING_STORE(EMB_STORES_KEY),

        /**
         * Configuration category for content retriever definitions.
         */
        CONTENT_RETRIEVER(CONTENT_RETRIEVERS_KEY);

        private final String key;

        Kind(String key) {
            this.key = key;
        }
    }
}



