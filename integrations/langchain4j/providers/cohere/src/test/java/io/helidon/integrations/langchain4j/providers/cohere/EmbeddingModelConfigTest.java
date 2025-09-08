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

package io.helidon.integrations.langchain4j.providers.cohere;

import java.time.Duration;

import io.helidon.config.Config;
import io.helidon.testing.junit5.Testing;

import org.junit.jupiter.api.Test;

import static io.helidon.integrations.langchain4j.providers.cohere.CohereConstants.ConfigCategory.MODEL;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@Testing.Test
class EmbeddingModelConfigTest {

    @Test
    void testDefaultRoot(Config c) {
        var config = CohereEmbeddingModelConfig.create(CohereConstants.create(c, MODEL, "test-model"));

        assertThat(config, is(notNullValue()));
        assertThat(config.apiKey().isPresent(), equalTo(true));
        assertThat(config.apiKey().get(), equalTo("api-key"));
        assertThat(config.modelName().isPresent(), equalTo(true));
        assertThat(config.modelName().get(), equalTo("model-name"));
        assertThat(config.baseUrl().isPresent(), equalTo(true));
        assertThat(config.baseUrl().get(), equalTo("base-url"));
        assertThat(config.inputType().isPresent(), is(true));
        assertThat(config.inputType().get(), is("inputType"));
        assertThat(config.timeout().isPresent(), is(true));
        assertThat(config.timeout().get(), equalTo(Duration.parse("PT10M")));
        assertThat(config.logRequests().isPresent(), is(true));
        assertThat(config.logRequests().get(), is(true));
        assertThat(config.logResponses().isPresent(), is(true));
        assertThat(config.logResponses().get(), is(true));
        assertThat(config.maxSegmentsPerBatch().isPresent(), is(true));
        assertThat(config.maxSegmentsPerBatch().get(), is(10));
    }
}
