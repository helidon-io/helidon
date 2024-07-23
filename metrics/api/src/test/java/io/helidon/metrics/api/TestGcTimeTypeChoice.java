/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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
package io.helidon.metrics.api;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigMappingException;
import io.helidon.config.ConfigSources;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Deprecated(since = "4.1", forRemoval = true)
class TestGcTimeTypeChoice {

    @Test
    void checkDefaultIsCounterForBackwardCompatibility() {
        Config config = Config.just(ConfigSources.create(Map.of()));
        MetricsConfig metricsConfig = MetricsConfig.create(config);
        assertThat("Defaulted gc.time type", metricsConfig.gcTimeType(), is(MetricsConfigBlueprint.GcTimeType.COUNTER));
    }

    @Test
    void checkExplicitCounter() {
        Config config = Config.just(ConfigSources.create(Map.of("gc-time-type", "counter")));
        MetricsConfig metricsConfig = MetricsConfig.create(config);
        assertThat("Explicit gc.time type as counter",
                   metricsConfig.gcTimeType(),
                   is(MetricsConfigBlueprint.GcTimeType.COUNTER));
    }

    @Test
    void checkGauge() {
        Config config = Config.just(ConfigSources.create(Map.of("gc-time-type", "gauge")));
        MetricsConfig metricsConfig = MetricsConfig.create(config);
        assertThat("Explicit gc.time type as gauge",
                   metricsConfig.gcTimeType(),
                   is(MetricsConfigBlueprint.GcTimeType.GAUGE));
    }

    @Test
    void checkInvalidSetting() {
        Config config = Config.just(ConfigSources.create(Map.of("gc-time-type", "bad")));
        assertThrows(ConfigMappingException.class, () ->MetricsConfig.create(config));
    }
}
