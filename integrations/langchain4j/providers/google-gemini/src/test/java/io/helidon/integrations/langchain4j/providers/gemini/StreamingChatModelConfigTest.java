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

package io.helidon.integrations.langchain4j.providers.gemini;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.langchain4j.providers.gemini.GoogleAiGeminiChatModelConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class StreamingChatModelConfigTest {

    @Test
    void testDefaultRoot() {
        var config = GoogleAiGeminiChatModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                .get(GoogleAiGeminiChatModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.modelName().isPresent(), is(true));
        assertThat(config.modelName().get(), is("model-name"));
        assertThat(config.baseUrl().isPresent(), is(true));
        assertThat(config.baseUrl().get(), is("base-url"));
        assertThat(config.temperature().isPresent(), is(true));
        assertThat(config.temperature().get(), is(36.6));
        assertThat(config.topK().isPresent(), is(true));
        assertThat(config.topK().get(), is(5));
        assertThat(config.topP().isPresent(), is(true));
        assertThat(config.topP().get(), is(10.0));
        assertThat(config.seed().isPresent(), is(true));
        assertThat(config.seed().get(), is(100));
        assertThat(config.timeout().isPresent(), is(true));
        assertThat(config.timeout().get(), equalTo(Duration.parse("PT10M")));
        assertThat(config.logRequests().isPresent(), is(true));
        assertThat(config.logRequests().get(), is(true));
        assertThat(config.logResponses().isPresent(), is(true));
        assertThat(config.logResponses().get(), is(true));
    }
}
