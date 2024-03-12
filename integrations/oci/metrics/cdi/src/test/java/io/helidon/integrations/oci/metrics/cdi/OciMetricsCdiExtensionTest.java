/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics.cdi;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.integrations.oci.metrics.OciMetricsSupport;
import io.helidon.integrations.oci.sdk.cdi.OciExtension;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.microprofile.config.ConfigCdiExtension;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static jakarta.interceptor.Interceptor.Priority.LIBRARY_BEFORE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@HelidonTest(resetPerTest = true)
@DisableDiscovery
// Add bean that will simulate oci metrics posting
@AddBean(OciMetricsCdiExtensionTest.MockOciMetricsBean.class)
// Helidon MP Extensions
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(ConfigCdiExtension.class)
@AddExtension(OciExtension.class)
// ConfigSources
@AddConfig(key = "ocimetrics.compartmentId",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.compartmentId)
@AddConfig(key = "ocimetrics.namespace",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.namespace)
@AddConfig(key = "ocimetrics.resourceGroup",
           value = OciMetricsCdiExtensionTest.MetricDataDetailsOCIParams.resourceGroup)
@AddConfig(key = "ocimetrics.initialDelay", value = "1")
@AddConfig(key = "ocimetrics.delay", value = "2")
class OciMetricsCdiExtensionTest {
    private static volatile int testMetricCount = 0;
    private static CountDownLatch countDownLatch = new CountDownLatch(1);
    private static PostMetricDataDetails postMetricDataDetails;
    private static boolean activateOciMetricsSupportIsInvoked;
    private static MeterRegistry registry = Metrics.globalRegistry();

    @AfterEach
    void resetState() {
        postMetricDataDetails = null;
        activateOciMetricsSupportIsInvoked = false;
        countDownLatch = new CountDownLatch(1);
    }

    @Test
    @AddConfig(key = "ocimetrics.enabled", value = "true")
    void testEnableOciMetrics() throws InterruptedException {
        validateOciMetricsSupport(true);
    }

    @Test
    void testEnableOciMetricsWithoutConfig() throws InterruptedException {
        validateOciMetricsSupport(true);
    }

    @Test
    @AddConfig(key = "ocimetrics.enabled", value = "false")
    void testDisableOciMetrics() throws InterruptedException {
        validateOciMetricsSupport(false);
    }

    private void validateOciMetricsSupport(boolean enabled) throws InterruptedException {
        Counter c1 = registry.getOrCreate(Counter.builder("baseDummyCounter")
                                     .scope(Meter.Scope.BASE));
        c1.increment();
        Counter c2 = registry.getOrCreate(Counter.builder("vendorDummyCounter")
                                     .scope(Meter.Scope.VENDOR));
        c2.increment();
        Counter c3 = registry.getOrCreate(Counter.builder("appDummyCounter")
                                     .scope(Meter.Scope.APPLICATION));
        c3.increment();

        // Wait for signal from metric update that testMetricCount has been retrieved
        if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
            // If Oci Metrics is enabled, this means that countdown() of CountDownLatch was never triggered, and hence should fail
            if (enabled) {
                fail("CountDownLatch timed out");
            }
        }

        if (enabled) {
            assertThat(activateOciMetricsSupportIsInvoked, is(true));
            // System meters in the registry might vary over time. Instead of looking for a specific number of meters,
            // make sure the three we added are in the OCI metric data.
            long dummyCounterCount = postMetricDataDetails.getMetricData().stream()
                    .filter(details -> details.getName().contains("DummyCounter"))
                    .count();
            assertThat(dummyCounterCount, is(3L));

            MetricDataDetails metricDataDetails = postMetricDataDetails.getMetricData().get(0);
            assertThat(metricDataDetails.getCompartmentId(),
                       is(MetricDataDetailsOCIParams.compartmentId));
            assertThat(metricDataDetails.getNamespace(), is(MetricDataDetailsOCIParams.namespace));
            assertThat(metricDataDetails.getResourceGroup(), is(MetricDataDetailsOCIParams.resourceGroup));
        } else {
            assertThat(activateOciMetricsSupportIsInvoked, is(false));
            assertThat(testMetricCount, is(0));
            // validate that OCI post metric is never called
            assertThat(postMetricDataDetails, is(equalTo(null)));
        }
        registry.remove(c1);
        registry.remove(c2);
        registry.remove(c3);
    }

    interface MetricDataDetailsOCIParams {
        String compartmentId = "dummy.compartmentId";
        String namespace = "dummy-namespace";
        String resourceGroup = "dummy_resourceGroup";
    }

    static class MockOciMetricsBean extends OciMetricsBean {
        @Override
        void registerOciMetrics(@Observes @Priority(LIBRARY_BEFORE + 20) @Initialized(ApplicationScoped.class) Object ignore,
                                Config rootConfig, Monitoring monitoringClient) {
            Monitoring mockedMonitoringClient = mock(Monitoring.class);
            when(mockedMonitoringClient.getEndpoint()).thenReturn("http://www.DummyEndpoint.com");
            doAnswer(invocationOnMock -> {
                PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
                postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
                testMetricCount = postMetricDataDetails.getMetricData().size();
                // Give signal that metrics has been posted
                countDownLatch.countDown();
                return PostMetricDataResponse.builder()
                        .__httpStatusCode__(200)
                        .build();
            }).when(mockedMonitoringClient).postMetricData(any());
            super.registerOciMetrics(ignore, rootConfig, mockedMonitoringClient);
        }

        // Override so we can test if this is invoked when enabled or skipped when disabled
        @Override
        protected void activateOciMetricsSupport(Config rootConfig, Config ociMetricsConfig, OciMetricsSupport.Builder builder) {
            activateOciMetricsSupportIsInvoked = true;
            super.activateOciMetricsSupport(rootConfig, ociMetricsConfig, builder);
        }
    }
}
