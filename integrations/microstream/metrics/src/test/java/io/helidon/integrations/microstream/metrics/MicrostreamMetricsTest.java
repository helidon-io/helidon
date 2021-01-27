/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.IOException;
import java.util.Date;

import org.eclipse.microprofile.metrics.Gauge;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.helidon.metrics.RegistryFactory;
import one.microstream.X;
import one.microstream.storage.embedded.types.EmbeddedStorageManager;
import one.microstream.storage.types.StorageRawFileStatistics;

class MicrostreamMetricsTest {
	private static EmbeddedStorageManager embeddedStorageManager;

	@BeforeAll
	static void init() throws IOException {
		embeddedStorageManager = Mockito.mock(EmbeddedStorageManager.class);
		
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
		Gauge<?> metric = findFirstGauge("microstream.globalFileCount");
		
		long value = (long) metric.getValue();
		assertThat("metric microstream.globalFileCount", value, is(42L));						
	}
	
	@Test
	void testLivDataLength() {								
		Gauge<?> metric = findFirstGauge("microstream.liveDataLength");
		
		long value = (long) metric.getValue();
		assertThat("metric microstream.liveDataLength", value, is(1001L));						
	}
	
	@Test
	void testTotalDataLength() {								
		Gauge<?> metric = findFirstGauge("microstream.totalDataLength");
		
		long value = (long) metric.getValue();
		assertThat("metric microstream.totalDataLength", value, is(2002L));						
	}
	
	private Gauge<?> findFirstGauge(String name) {
		MetricRegistry metricsRegistry = RegistryFactory.getInstance().getRegistry(MetricRegistry.Type.VENDOR);		
		MetricID id = metricsRegistry.getGauges(new MetricNameFilter(name)).firstKey();		
		return metricsRegistry.getGauges().get(id);
	}
	
	private static class MetricNameFilter implements MetricFilter {
        private final String name;

        private MetricNameFilter(String name) {
            this.name = name;
        }

        @Override
        public boolean matches(MetricID metricID, Metric metric) {
            return metricID.getName().equals(name);
        }
    }
}
