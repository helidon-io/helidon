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
import io.helidon.metrics.api.MetricsConfig;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

class TestPublisherConfig {

    @Test
    void testPrometheusConfig() {
        String configText = """
                metrics:
                  publishers:
                    - type: micrometer-prometheus
                      name: one-second
                      step: "PT1S"
                    - type: micrometer-prometheus
                      name: one-minute
                      step: "PT60S"
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }

    @Test
    void testMixed() {
        String configText = """
                metrics:
                  publishers:
                    - type: micrometer-prometheus
                      name: one-second
                      step: "PT1S"
                    - type: micrometer-otlp
                      name: my-otlp
                      url: "http://localhost:8080/somewhere"
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }

    @Test
    void verifyBriefPrometheusMention() {
        // Make sure that just mentioning a publisher type without any lower-level settings works.
        String configText = """
                metrics:
                  publishers:
                    micrometer-otlp:
                    micrometer-prometheus:
                """;

        var metricsConfig = MetricsConfig.create(Config.just(configText, MediaTypes.APPLICATION_YAML)
                                                         .get("metrics"));

        assertThat("Publishers",
                   metricsConfig.publishers(),
                   hasSize(2));
    }
}
