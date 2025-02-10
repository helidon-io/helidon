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
import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.metrics.api.Timer;

import org.junit.jupiter.api.Test;

import static io.helidon.metrics.systemmeters.MeterBuilderMatcher.withName;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.COUNT;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.METER_NAME_PREFIX;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.RECENT_PINNED;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.STARTS;
import static io.helidon.metrics.systemmeters.VThreadSystemMetersProvider.SUBMIT_FAILURES;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;

class TestVirtualThreadsMetersConfigs {

    @Test
    void checkDefault() {
        Config config = Config.just(ConfigSources.create(Map.of()));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        var meterBuilders = provider.meterBuilders(metricsFactory);
        assertThat("Meter builders with default config", meterBuilders, empty());
    }

    @Test
    void checkVirtualThreadCountMetersEnabled() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        var meterBuilders = provider.meterBuilders(metricsFactory);

        assertThat("Default meter builders",
                   meterBuilders,
                   containsInAnyOrder(allOf(withName(equalTo(METER_NAME_PREFIX + PINNED)),
                                            instanceOf(Gauge.Builder.class)),
                                      allOf(withName(equalTo(METER_NAME_PREFIX + SUBMIT_FAILURES)),
                                            instanceOf(Gauge.Builder.class)),
                                      allOf(withName(equalTo(METER_NAME_PREFIX + RECENT_PINNED)),
                                            instanceOf(Timer.Builder.class)),
                                      allOf(withName(equalTo(METER_NAME_PREFIX + COUNT)),
                                            instanceOf(Gauge.Builder.class)),
                                      allOf(withName(equalTo(METER_NAME_PREFIX + STARTS)),
                                            instanceOf(Gauge.Builder.class))));

    }

    @Test
    void checkPinnedThreadThreshold() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);

        assertThat("Pinned thread threshold", provider.pinnedVirtualThreadsThresholdMillis(), equalTo(40L));

    }

    @Test
    void checkRecentPinnedTimerLookup() {
        Config config = Config.just(ConfigSources.create(Map.of("virtual-threads.enabled", "true",
                                                                "virtual-threads.pinned.threshold", "PT0.040S")));
        MetricsFactory metricsFactory = MetricsFactory.getInstance(config);
        VThreadSystemMetersProvider provider = new VThreadSystemMetersProvider();
        provider.meterBuilders(metricsFactory);

        provider.findPinned();
    }

}
