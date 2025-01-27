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

package io.helidon.integrations.langchain4j.providers.openai;

import java.net.Proxy;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.integrations.langchain4j.RegistryHelper;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.model.openai.OpenAiModerationModel;

/**
 * Factory class for creating a configured {@link OpenAiModerationModel}.
 *
 * <p>This factory automatically registers a bean in the CDI registry if the configuration property
 * <i>langchain4j.open-ai.moderation-model.enabled</i> is set to <i>true</i>.</p>
 *
 * @see dev.langchain4j.model.openai.OpenAiModerationModel
 * @see OpenAiModerationModelConfig
 */
@Service.Singleton
@Service.Named("*")
class OpenAiModerationModelFactory implements Service.ServicesFactory<OpenAiModerationModel> {
    private final OpenAiModerationModel model;
    private final boolean enabled;

    OpenAiModerationModelFactory(ServiceRegistry registry, Config config) {
        var modelConfig = OpenAiModerationModelConfig.create(config.get(OpenAiModerationModelConfigBlueprint.CONFIG_ROOT));

        this.enabled = modelConfig.enabled();

        if (enabled) {
            this.model = buildModel(registry, modelConfig);
        } else {
            this.model = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiModerationModel>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(model),
                           Service.QualifiedInstance.create(model, OpenAi.OPEN_AI_QUALIFIER));
        }
        return List.of();
    }

    /**
     * Creates and returns an {@link OpenAiModerationModel} configured using the provided configuration.
     *
     * @param config the properties used to configure the {@link OpenAiModerationModel}
     * @return a configured instance of {@link OpenAiModerationModel}
     */
    private static OpenAiModerationModel buildModel(ServiceRegistry registry, OpenAiModerationModelConfig config) {
        var builder = OpenAiModerationModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.proxy().ifPresent(p -> builder.proxy(RegistryHelper.named(registry, Proxy.class, p)));
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }
}
