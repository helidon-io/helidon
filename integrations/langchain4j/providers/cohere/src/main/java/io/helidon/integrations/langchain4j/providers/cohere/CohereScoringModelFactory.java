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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.net.Proxy;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

import dev.langchain4j.model.cohere.CohereScoringModel;

/**
 * Factory for a configured {@link dev.langchain4j.model.cohere.CohereScoringModel}.
 *
 * @see dev.langchain4j.model.cohere.CohereScoringModel
 * @see io.helidon.integrations.langchain4j.providers.cohere.CohereScoringModelConfig
 * @see #create
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(Cohere.WEIGHT)
public class CohereScoringModelFactory implements Service.ServicesFactory<CohereScoringModel> {
    private final Supplier<Optional<CohereScoringModel>> model;

    CohereScoringModelFactory(@Service.Named(Cohere.SCORING_MODEL) Supplier<Optional<Proxy>> cohereScoringModelProxy,
                              @Service.Named(Cohere.COHERE) Supplier<Optional<Proxy>> cohereProxy,
                              Supplier<Optional<Proxy>> proxy,
                              Config config) {
        var configBuilder = CohereScoringModelConfig.builder()
                .config(config.get(CohereScoringModelConfigBlueprint.CONFIG_ROOT));

        this.model = () -> buildModel(configBuilder, cohereScoringModelProxy, cohereProxy, proxy);
    }

    /**
     * Create the Cohere model from its configuration.
     *
     * @param config configuration to use
     * @return a new model instance
     * @throws IllegalStateException in case the configuration is not enabled
     */
    public static CohereScoringModel create(CohereScoringModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }

        var builder = CohereScoringModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.modelName().ifPresent(builder::modelName);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.proxy().ifPresent(builder::proxy);
        return builder.build();
    }

    @Override
    public List<Service.QualifiedInstance<CohereScoringModel>> services() {
        var modelOptional = model.get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }

        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, Cohere.COHERE_QUALIFIER));
    }

    private static Optional<CohereScoringModel> buildModel(CohereScoringModelConfig.Builder configBuilder,
                                                           Supplier<Optional<Proxy>> cohereScoringModelProxy,
                                                           Supplier<Optional<Proxy>> cohereProxy,
                                                           Supplier<Optional<Proxy>> proxy) {
        if (!configBuilder.enabled()) {
            return Optional.empty();
        }

        cohereScoringModelProxy.get()
                .or(cohereProxy)
                .or(proxy)
                .ifPresent(configBuilder::proxy);

        return Optional.of(create(configBuilder.build()));
    }
}
