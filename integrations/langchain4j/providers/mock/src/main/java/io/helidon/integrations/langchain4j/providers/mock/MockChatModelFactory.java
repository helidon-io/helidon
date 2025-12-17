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

package io.helidon.integrations.langchain4j.providers.mock;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.service.registry.Service;

/**
 * Service factory for the MockChatModel.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class MockChatModelFactory implements Service.ServicesFactory<MockChatModel> {

    private final Supplier<Optional<MockChatModel>> model;

    /**
     * Creates a new MockChatModelFactory.
     *
     * @param config Configuration for the new model.
     */
    MockChatModelFactory(Config config) {
        var configBuilder = MockChatModelConfig.builder()
                .config(config.get(MockChatModelConfigBlueprint.CONFIG_ROOT));
        model = () -> buildModel(configBuilder);
    }

    /**
     * Builds a new model configured with the given configuration builder.
     *
     * @param configBuilder Configuration builder for the new model.
     * @return New model configured with the given configuration builder.
     */
    protected static Optional<MockChatModel> buildModel(MockChatModelConfig.Builder configBuilder) {
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
        var modelOptional = model().get();
        if (modelOptional.isEmpty()) {
            return List.of();
        }
        var theModel = modelOptional.get();
        return List.of(Service.QualifiedInstance.create(theModel),
                       Service.QualifiedInstance.create(theModel, MockConstants.QUALIFIER));
    }

    private Supplier<Optional<MockChatModel>> model() {
        return model;
    }

}
