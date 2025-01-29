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
import dev.langchain4j.model.openai.OpenAiChatModel;

/*
This is a ServicesFactory, as we want to produce two instances - a named one, and an unnamed one
 */
@Service.Singleton
@Service.Named("*")
class OpenAiChatModelFactory implements Service.ServicesFactory<OpenAiChatModel> {
    private final OpenAiChatModel model;
    private final boolean enabled;

    OpenAiChatModelFactory(ServiceRegistry registry, Config config) {
        var modelConfig = OpenAiChatModelConfig.create(config.get(OpenAiChatModelConfigBlueprint.CONFIG_ROOT));

        this.enabled = modelConfig.enabled();

        if (enabled) {
            this.model = buildModel(registry, modelConfig);
        } else {
            this.model = null;
        }
    }

    @Override
    public List<Service.QualifiedInstance<OpenAiChatModel>> services() {
        if (enabled) {
            return List.of(Service.QualifiedInstance.create(model),
                           Service.QualifiedInstance.create(model, OpenAi.OPEN_AI_QUALIFIER));
        }
        return List.of();
    }

    private static OpenAiChatModel buildModel(ServiceRegistry registry, OpenAiChatModelConfig config) {
        var builder = OpenAiChatModel.builder();
        config.baseUrl().ifPresent(builder::baseUrl);
        config.apiKey().ifPresent(builder::apiKey);
        config.organizationId().ifPresent(builder::organizationId);
        config.modelName().ifPresent(builder::modelName);
        config.temperature().ifPresent(builder::temperature);
        config.topP().ifPresent(builder::topP);
        if (!config.stop().isEmpty()) {
            builder.stop(config.stop());
        }
        config.maxTokens().ifPresent(builder::maxTokens);
        config.maxCompletionTokens().ifPresent(builder::maxCompletionTokens);
        config.presencePenalty().ifPresent(builder::presencePenalty);
        config.frequencyPenalty().ifPresent(builder::frequencyPenalty);
        if (!config.logitBias().isEmpty()) {
            builder.logitBias(config.logitBias());
        }
        config.responseFormat().ifPresent(builder::responseFormat);
        config.strictJsonSchema().ifPresent(builder::strictJsonSchema);
        config.seed().ifPresent(builder::seed);
        config.user().ifPresent(builder::user);
        config.strictTools().ifPresent(builder::strictTools);
        config.parallelToolCalls().ifPresent(builder::parallelToolCalls);
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
