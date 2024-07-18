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

package io.helidon.integrations.eclipsestore.metrics;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Metrics;
import org.eclipse.serializer.util.X;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.eclipse.store.storage.types.StorageRawFileStatistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

// TODO disabled
@Disabled
class EclipseStoreHealthMetricsTest {

    @BeforeAll
    static void init() {
        EmbeddedStorageManager embeddedStorageManager = Mockito.mock(EmbeddedStorageManager.class);

        Mockito.when(embeddedStorageManager.createStorageStatistics()).thenReturn(
                StorageRawFileStatistics.New(
                        new Date(System.currentTimeMillis()),
                        42,
                        1001,
                        2002,
                        X.emptyTable()));

        EclipseStoreHealthMetricsSupport.builder(embeddedStorageManager).build().registerMetrics();
    }

    @Test
    void testGlobalFileCount() {
        var firstGauge = findFirstGauge("eclipsestore.globalFileCount");

        long value = (long) firstGauge.value();
        assertThat("firstGauge eclipsestore.globalFileCount", value, is(42L));
    }

    @Test
    void testLivDataLength() {
        var metricGauge = findFirstGauge("eclipsestore.liveDataLength");

        long value = (long) metricGauge.value();
        assertThat("metric eclipsestore.liveDataLength", value, is(1001L));
    }

    @Test
    void testTotalDataLength() {
        var firstGauge = findFirstGauge("eclipsestore.totalDataLength");

        long value = (long) firstGauge.value();
        assertThat("firstGauge eclipsestore.totalDataLength", value, is(2002L));
    }

    private Gauge<?> findFirstGauge(String name) {
        return Metrics.globalRegistry().gauge(name, Set.of()).orElse(null);
    }

}
