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
package io.helidon.metrics.systemmeters;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.MetricsFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class TestVirtualThreadsMetersConfigs {

    @Test
    void checkExceptionWithBadConfigValue() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.configuration", "badValue")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        assertThrows(RuntimeException.class, () -> provider.meterBuilders(metricsFactory));
    }

    @Test void checkCustomFilePath() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.configuration",
                                                                "src/test/resources/metrics-test.jfc")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);
    }

}
