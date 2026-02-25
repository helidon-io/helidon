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

package io.helidon.metrics.providers.micrometer;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

class TestRegistryConfig {

    @Test
    void testConfiguredRegistries() {

        var configText = """
                metrics:
                  registries:
                    otlp:
                      step: PT1S
                """;

        var config = Config.just(configText, MediaTypes.APPLICATION_YAML);
        var metrics = MicrometerMetricsConfig.create(config.get("metrics"));

        assertThat("Registries", metrics.meterRegistries(), hasSize(greaterThanOrEqualTo(1)));
    }
}
