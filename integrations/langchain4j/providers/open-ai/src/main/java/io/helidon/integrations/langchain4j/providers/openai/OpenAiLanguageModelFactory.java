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

import dev.langchain4j.model.Tokenizer;
import dev.langchain4j.model.openai.OpenAiLanguageModel;

/**
 * Factory class for creating a configured {@link OpenAiLanguageModel}.
 *
 * <p>This factory automatically registers a bean in the CDI registry if the configuration property
 * <i>langchain4j.open-ai.language-model.enabled</i> is set to <i>true</i>.</p>
 *
 * @see OpenAiLanguageModel
 * @see OpenAiLanguageModelConfig
 */
@Service.Singleton
@Service.Named("*")
class OpenAiLanguageModelFactory implements Service.ServicesFactory<OpenAiLanguageModel> {
    private final OpenAiLanguageModel model;
    private final boolean enabled;

    OpenAiLanguageModelFactory(ServiceRegistry registry, Config config) {
        var modelConfig = OpenAiLanguageModelConfig.create(config.get(OpenAiLanguageModelConfig.CONFIG_ROOT));

        this.enabled = modelConfig.enabled();

        if (enabled) {
            this.model = buildModel(registry, modelConfig);
        } else {
            this.model = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiLanguageModel>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(model),
                           Service.QualifiedInstance.create(model, OpenAi.OPEN_AI_QUALIFIER));
        }
        return List.of();
    }

    /**
     * Creates and returns an {@link OpenAiLanguageModel} configured using the provided configuration.
     *
     * @param config the properties used to configure the {@link OpenAiLanguageModel}
     * @return a configured instance of {@link OpenAiLanguageModel}
     */
    private static OpenAiLanguageModel buildModel(ServiceRegistry registry, OpenAiLanguageModelConfig config) {
        var builder = OpenAiLanguageModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.temperature().ifPresent(builder::temperature);
        config.timeout().ifPresent(builder::timeout);
        config.maxRetries().ifPresent(builder::maxRetries);
        config.logRequests().ifPresent(builder::logRequests);
        config.logResponses().ifPresent(builder::logResponses);
        config.tokenizer().ifPresent(t -> builder.tokenizer(RegistryHelper.named(registry, t, Tokenizer.class)));
        config.proxy().ifPresent(p -> builder.proxy(RegistryHelper.named(registry, p, Proxy.class)));
        if (!config.customHeaders().isEmpty()) {
            builder.customHeaders(config.customHeaders());
        }
        return builder.build();
    }
}
