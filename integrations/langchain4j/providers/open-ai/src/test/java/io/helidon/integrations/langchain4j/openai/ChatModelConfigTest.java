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

package io.helidon.integrations.langchain4j.openai;

import java.time.Duration;
import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.langchain4j.providers.openai.OpenAiChatModelConfig;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class ChatModelConfigTest {

    @Test
    void testDefaultRoot() {
        var config = OpenAiChatModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                .get(OpenAiChatModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.apiKey().isPresent(), is(true));
        assertThat(config.apiKey().get(), is("api-key"));
        assertThat(config.modelName().isPresent(), is(true));
        assertThat(config.modelName().get(), is("model-name"));
        assertThat(config.baseUrl().isPresent(), is(true));
        assertThat(config.baseUrl().get(), is("base-url"));
        assertThat(config.organizationId().isPresent(), is(true));
        assertThat(config.organizationId().get(), is("organization-id"));
        assertThat(config.temperature().isPresent(), is(true));
        assertThat(config.temperature().get(), is(36.6));
        assertThat(config.topP().isPresent(), is(true));
        assertThat(config.topP().get(), is(10.0));
        assertThat(config.stop(), is(notNullValue()));
        assertThat(config.stop().size(), is(3));
        assertThat(config.stop().get(0), is("stop1"));
        assertThat(config.stop().get(1), is("stop2"));
        assertThat(config.stop().get(2), is("stop3"));
        assertThat(config.maxTokens().isPresent(), is(true));
        assertThat(config.maxTokens().get(), is(15));
        assertThat(config.maxCompletionTokens().isPresent(), is(true));
        assertThat(config.maxCompletionTokens().get(), is(20));
        assertThat(config.presencePenalty().isPresent(), is(true));
        assertThat(config.presencePenalty().get(), is(0.1));
        assertThat(config.frequencyPenalty().isPresent(), is(true));
        assertThat(config.frequencyPenalty().get(), is(0.2));
        assertThat(config.logitBias().size(), is(2));
        assertThat(config.logitBias().get("key1"), is(1));
        assertThat(config.logitBias().get("key2"), is(2));
        assertThat(config.responseFormat().isPresent(), is(true));
        assertThat(config.responseFormat().get(), is("response-format"));
        assertThat(config.strictJsonSchema().isPresent(), is(true));
        assertThat(config.strictJsonSchema().get(), is(false));
        assertThat(config.seed().isPresent(), is(true));
        assertThat(config.seed().get(), is(100));
        assertThat(config.user().isPresent(), is(true));
        assertThat(config.user().get(), is("user"));
        assertThat(config.strictTools().isPresent(), is(true));
        assertThat(config.strictTools().get(), is(false));
        assertThat(config.parallelToolCalls().isPresent(), is(true));
        assertThat(config.parallelToolCalls().get(), is(false));
        assertThat(config.timeout().isPresent(), is(true));
        assertThat(config.timeout().get(), equalTo(Duration.parse("PT10M")));
        assertThat(config.maxRetries().isPresent(), is(true));
        assertThat(config.maxRetries().get(), is(3));
        assertThat(config.logRequests().isPresent(), is(true));
        assertThat(config.logRequests().get(), is(true));
        assertThat(config.logResponses().isPresent(), is(true));
        assertThat(config.logResponses().get(), is(true));
        assertThat(config.customHeaders().size(), is(2));
        assertThat(config.customHeaders().get("header1"), is(equalTo("value1")));
        assertThat(config.customHeaders().get("header2"), is(equalTo("value2")));
    }

    @Test
    void testNamedClient(ServiceRegistry registry) {

        var yaml = """
                langchain4j.open-ai:
                  chat-model:
                    http-client-builder.service-registry.named: "namedHttpClient"
                """;

        var config = OpenAiChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(Config.just(ConfigSources.create(yaml, MediaTypes.APPLICATION_X_YAML))
                                .get(OpenAiChatModelConfig.CONFIG_ROOT))
                .build();

        assertThat(config.httpClientBuilder().map(builder -> builder.build().execute(null).body()), is(Optional.of("namedHttpClient")));
    }

    @Test
    void testDefaultClient(ServiceRegistry registry) {

        var config = OpenAiChatModelConfig.builder()
                .serviceRegistry(registry)
                .config(Config.empty())
                .build();

        assertThat(config.httpClientBuilder().map(builder -> builder.build().execute(null).body()), is(Optional.of("defaultHttpClient")));
    }
}
