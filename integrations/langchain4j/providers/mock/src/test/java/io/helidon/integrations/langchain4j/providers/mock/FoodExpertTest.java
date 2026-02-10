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

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.langchain4j.Ai;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;

import dev.langchain4j.service.SystemMessage;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@Testing.Test
public class FoodExpertTest {

    @Test
    void customMockResponse() {
        // language=YAML
        var overrideConfig = """
                langchain4j:
                  services:
                    food-service:
                      chat-model: test-mock-food-model
                  models:
                    test-mock-food-model:
                      provider: helidon-mock
                      rules:
                        - pattern: .*pizza.*ananas.*
                          response: Don't!
                        - pattern: .*Return this message:\\s+'([^']+)'.*
                          template: "The message is: $1"
                """;
        Services.set(Config.class,
                     Config.builder()
                             .addSource(ConfigSources.create(overrideConfig, MediaTypes.APPLICATION_X_YAML))
                             .addSource(ConfigSources.create(Config.create()))
                             .build());
        var aiService = Services.get(FoodExpertAiService.class);
        assertThat(aiService.chat("I can prepare pizza with ananas!"), is("Don't!"));
        assertThat(aiService.chat("Return this message: 'test-message'"), is("The message is: test-message"));
    }

    @Ai.Service("food-service")
    @Ai.ChatModel("production-chatgpt-model")
    interface FoodExpertAiService {

        @SystemMessage("You are a food expert!")
        String chat(String prompt);
    }
}
