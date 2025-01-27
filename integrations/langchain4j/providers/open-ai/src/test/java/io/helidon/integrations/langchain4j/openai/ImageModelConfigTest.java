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

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.langchain4j.providers.openai.OpenAiImageModelConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class ImageModelConfigTest {

    @Test
    void testDefaultRoot() {
        var config = OpenAiImageModelConfig.create(Config.just(ConfigSources.classpath("application.yaml"))
                                                           .get(OpenAiImageModelConfig.CONFIG_ROOT));

        assertThat(config, is(notNullValue()));
        assertThat(config.apiKey().isPresent(), is(true));
        assertThat(config.apiKey().get(), is("api-key"));
        assertThat(config.modelName().isPresent(), is(true));
        assertThat(config.modelName().get(), is("model-name"));
        assertThat(config.baseUrl().isPresent(), is(true));
        assertThat(config.baseUrl().get(), is("base-url"));
        assertThat(config.organizationId().isPresent(), is(true));
        assertThat(config.organizationId().get(), is("organization-id"));
        assertThat(config.size().isPresent(), is(true));
        assertThat(config.size().get(), is("size"));
        assertThat(config.quality().isPresent(), is(true));
        assertThat(config.quality().get(), is("quality"));
        assertThat(config.style().isPresent(), is(true));
        assertThat(config.style().get(), is("style"));
        assertThat(config.user().isPresent(), is(true));
        assertThat(config.user().get(), is("user"));
        assertThat(config.responseFormat().isPresent(), is(true));
        assertThat(config.responseFormat().get(), is("response-format"));
        assertThat(config.timeout().isPresent(), is(true));
        assertThat(config.timeout().get(), equalTo(Duration.parse("PT10M")));
        assertThat(config.maxRetries().isPresent(), is(true));
        assertThat(config.maxRetries().get(), is(3));
        assertThat(config.logRequests().isPresent(), is(true));
        assertThat(config.logRequests().get(), is(true));
        assertThat(config.logResponses().isPresent(), is(true));
        assertThat(config.logResponses().get(), is(true));
        assertThat(config.withPersisting().isPresent(), is(true));
        assertThat(config.withPersisting().get(), is(true));
        assertThat(config.persistTo().isPresent(), is(true));
        assertThat(config.persistTo().get(), is("temp"));
        assertThat(config.customHeaders().size(), is(2));
        assertThat(config.customHeaders().get("header1"), is(equalTo("value1")));
        assertThat(config.customHeaders().get("header2"), is(equalTo("value2")));
        assertThat(config.proxy().isPresent(), is(true));
        assertThat(config.proxy().get(), is(equalTo("discover:auto")));
    }
}
