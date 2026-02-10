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

package io.helidon.integrations.langchain4j.providers.mock;

import java.util.List;
import java.util.Optional;

import io.helidon.common.Weight;
import io.helidon.config.Config;
import io.helidon.service.registry.Qualifier;
import io.helidon.service.registry.Service;

/**
 * Service factory for the MockStreamingChatModel.
 */
@Service.Singleton
@Service.Named(Service.Named.WILDCARD_NAME)
@Weight(98.0D)
class MockStreamingChatModelFactory implements Service.ServicesFactory<MockStreamingChatModel> {

    private final Config config;
    private final List<String> modelNames;
    private final List<MockChatRule> rules;

    /**
     * Creates a new MockStreamingChatModelFactory.
     *
     * @param config Configuration for the new model.
     */
    MockStreamingChatModelFactory(Config config, List<MockChatRule> rules) {
        this.config = config;
        this.modelNames = MockConstants.modelNames(config,
                                                 MockStreamingChatModel.class,
                                                 MockLc4jProvider.PROVIDER_KEY);
        this.rules = rules;
    }

    /**
     * Builds a new model configured with the given configuration builder.
     *
     * @param modelName Model name.
     * @param config    Configuration for the new model.
     * @param rules     List of {@link MockChatRule}s to attach to the model.
     * @return New model configured with the given configuration builder.
     */
    protected static Optional<MockStreamingChatModel> buildModel(String modelName, Config config, List<MockChatRule> rules) {
        MockStreamingChatModelConfig.Builder configBuilder = MockStreamingChatModelConfig.builder()
                .config(MockConstants.create(config, MockStreamingChatModel.class, modelName));

        if (!configBuilder.enabled()) {
            return Optional.empty();
        }
        configBuilder.addRules(rules);
        return Optional.of(configBuilder.buildPrototype().build());
    }

    @Override
    public List<Service.QualifiedInstance<MockStreamingChatModel>> services() {
        return modelNames.stream()
                .map(name -> buildModel(name, config, rules)
                        .map(model -> Service.QualifiedInstance
                                .create(model, Qualifier.createNamed(name))))
                .flatMap(Optional::stream)
                .toList();
    }
}
