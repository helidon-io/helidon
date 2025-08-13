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

package io.helidon.builder.test;

import java.util.Map;

import io.helidon.builder.test.testsubjects.OtelTracing;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;

class TestFactoryMethod {
     @Test
    void testConfigFactory() {
        var config = Config.just(ConfigSources.create(Map.of("processors", "")));

        var otelConfig = OtelTracing.create(config);
        var processors = otelConfig.processors();

        assertThat(processors, hasSize(1));
        // returned from here: io.helidon.builder.test.testsubjects.OtelTracingSupport.createProcessors
        assertThat(processors.getFirst().configured(), is("some-value"));
    }
}
