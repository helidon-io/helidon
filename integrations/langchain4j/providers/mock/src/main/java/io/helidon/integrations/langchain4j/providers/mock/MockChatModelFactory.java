/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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

package io.helidon.integrations.langchain4j.providers.mock;

import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.integrations.langchain4j.ConfigUtils;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Service factory for the MockChatModel.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class MockChatModelFactory implements Service.ServicesFactory<MockChatModel> {

    private final Config config;
    private final List<String> modelNames;

    /**
     * Creates a new MockChatModelFactory.
     *
     * @param config Configuration for the new model.
     */
    MockChatModelFactory(Config config) {
        this.config = config;
        this.modelNames = ConfigUtils.modelNames(config, MockChatModel.class, MockChatModelConfigBlueprint.PROVIDER_KEY);
    }

    /**
     * Builds a new model configured with the given configuration builder.
     *
     * @param modelName Model name.
     * @param config    Configuration for the new model.
     * @return New model configured with the given configuration builder.
     */
    protected static Optional<MockChatModel> buildModel(String modelName, Config config) {
        MockChatModelConfig.Builder configBuilder = MockChatModelConfig.builder()
                .config(ConfigUtils.create(config, MockChatModel.class, modelName));

        if (!configBuilder.enabled()) {
            return Optional.empty();
        }
        return Optional.of(create(configBuilder.buildPrototype()));
    }

    /**
     * Creates a new model configured with the given configuration.
     *
     * @param config Configuration for the new model.
     * @return New model configured with the given configuration.
     */
    static MockChatModel create(MockChatModelConfig config) {
        if (!config.enabled()) {
            throw new IllegalStateException("Cannot create a model when the configuration is disabled.");
        }
        return config.build();
    }

    @Override
    public List<Service.QualifiedInstance<MockChatModel>> services() {
        return modelNames.stream()
                .map(name -> buildModel(name, config)
                        .map(model -> Service.QualifiedInstance
                                .create(model, Qualifier.createNamed(name))))
                .flatMap(Optional::stream)
                .toList();
    }
}
