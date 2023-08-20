/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.microstream.metrics;

import java.util.Date;
import java.util.Set;

import io.helidon.metrics.api.Gauge;
import io.helidon.metrics.api.Metrics;

import one.microstream.X;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import one.microstream.storage.types.StorageRawFileStatistics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class MicrostreamMetricsTest {

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

        MicrostreamMetricsSupport.builder(embeddedStorageManager).build().registerMetrics();
    }

    @Test
    void testGlobalFileCount() {
        Gauge metric = findFirstGauge("microstream.globalFileCount");

        long value = (long) metric.value();
        assertThat("metric microstream.globalFileCount", value, is(42L));
    }

    @Test
    void testLivDataLength() {
        Gauge metric = findFirstGauge("microstream.liveDataLength");

        long value = (long) metric.value();
        assertThat("metric microstream.liveDataLength", value, is(1001L));
    }

    @Test
    void testTotalDataLength() {
        Gauge metric = findFirstGauge("microstream.totalDataLength");

        long value = (long) metric.value();
        assertThat("metric microstream.totalDataLength", value, is(2002L));
    }

    private Gauge findFirstGauge(String name) {
        return Metrics.globalRegistry().getGauge(name, Set.of()).orElse(null);
    }

}
