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
import java.nio.file.Path;
import java.util.List;

import io.helidon.common.config.Config;
import io.helidon.integrations.langchain4j.RegistryHelper;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;

import dev.langchain4j.model.openai.OpenAiImageModel;

/**
 * Factory class for creating a configured {@link OpenAiImageModel}.
 *
 * <p>This factory automatically registers a bean in the CDI registry if the configuration property
 * <i>langchain4j.open-ai.image-model.enabled</i> is set to <i>true</i>.</p>
 *
 * @see OpenAiImageModel
 * @see OpenAiImageModelConfig
 */
@Service.Singleton
@Service.Named("*")
class OpenAiImageModelFactory implements Service.ServicesFactory<OpenAiImageModel> {
    private final OpenAiImageModel model;
    private final boolean enabled;

    OpenAiImageModelFactory(ServiceRegistry registry, Config config) {
        var modelConfig = OpenAiImageModelConfig.create(config.get(OpenAiImageModelConfigBlueprint.CONFIG_ROOT));

        this.enabled = modelConfig.enabled();

        if (enabled) {
            this.model = buildModel(registry, modelConfig);
        } else {
            this.model = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiImageModel>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(model),
                           Service.QualifiedInstance.create(model, OpenAi.OPEN_AI_QUALIFIER));
        }
        return List.of();
    }

    /**
     * Creates and returns an {@link OpenAiImageModel} configured using the provided configuration.
     *
     * @param config the properties used to configure the {@link OpenAiImageModel}
     * @return a configured instance of {@link OpenAiImageModel}
     */
    private static OpenAiImageModel buildModel(ServiceRegistry registry, OpenAiImageModelConfig config) {
        var builder = OpenAiImageModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.size().ifPresent(builder::size);
        config.quality().ifPresent(builder::quality);
        config.style().ifPresent(builder::style);
        config.user().ifPresent(builder::user);
        config.responseFormat().ifPresent(builder::responseFormat);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.withPersisting().ifPresent(builder::withPersisting);
        config.persistTo().ifPresent(value -> builder.persistTo(Path.of(value)));
        config.proxy().ifPresent(p -> builder.proxy(RegistryHelper.named(registry, Proxy.class, p)));
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }
}
