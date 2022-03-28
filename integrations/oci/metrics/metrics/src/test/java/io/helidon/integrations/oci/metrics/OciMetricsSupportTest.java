/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.integrations.oci.metrics;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.monitoring.MonitoringClient;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metric;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.MetricRegistry.Type;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class OciMetricsSupportTest {
    private final OciMetricsSupport.NameFormatter nameFormatter = new OciMetricsSupport.NameFormatter() { };
    private static final MonitoringClient monitoringClient = mock(MonitoringClient.class);
    private final Type[] types = {Type.BASE, Type.VENDOR, Type.APPLICATION};

    private static int testMetricUpdatePostMetricDataCallCount = 0;
    private static Double testMetricUpdateCounterValue;
    private static int testMetricCount = 0;

    private final RegistryFactory rf = RegistryFactory.getInstance();
    private final MetricRegistry baseMetricRegistry = rf.getRegistry(Type.BASE);
    private final MetricRegistry vendorMetricRegistry = rf.getRegistry(Type.VENDOR);
    private final MetricRegistry appMetricRegistry = rf.getRegistry(Type.APPLICATION);

    @BeforeEach
    private void beforeEach() {
        // clear all registry
        for (Type type: types) {
            MetricRegistry metricRegistry = rf.getRegistry(type);
            metricRegistry.removeMatching(new MetricFilter() {
                @Override
                public boolean matches(MetricID metricID, Metric metric) {
                    return true;
                }
            });
        }
    }

    @Test
    public void testMetricUpdate() {
        // mock monitoringClient.postMetricData()
        doAnswer(invocationOnMock -> {
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            List<MetricDataDetails> allMetricDataDetails = postMetricDataDetails.getMetricData();
            testMetricUpdateCounterValue = allMetricDataDetails.get(0).getDatapoints().get(0).getValue();
            testMetricUpdatePostMetricDataCallCount++;
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());

        Counter counter = baseMetricRegistry.counter("DummyCounter");

        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .compartmentId("compartmentId")
                .namespace("namespace")
                .resourceGroup("resourceGroup")
                .initialDelay(1L)
                .delay(2L)
                .descriptionEnabled(false)

                .monitoringClient(monitoringClient);

        Routing routing = createRouting(ociMetricsSupportBuilder);

        counter.inc();
        WebServer webServer = createWebServer(routing);

        delay(1000L);
        counter.inc();

        Timer timer = new Timer(10);
        while (testMetricUpdatePostMetricDataCallCount < 2) {
            if (timer.expired()) {
                fail(String.format("Timed out after %d sec. waiting for 2 Monitoring.postMetricData() calls",
                        timer.getTimeout()));
            }
            delay(50L);
        }
        assertThat(testMetricUpdateCounterValue.intValue(), is(equalTo(2)));
        webServer.shutdown();
    }

    @Test
    public void testConfigSources() {
        mockPostMetricDataAndGetTestMetricCount();

        baseMetricRegistry.counter("baseDummyCounter1").inc();

        vendorMetricRegistry.counter("vendorDummyCounter1").inc();
        vendorMetricRegistry.counter("vendorDummyCounter2").inc();

        appMetricRegistry.counter("appDummyCounter1").inc();
        appMetricRegistry.counter("appDummyCounter2").inc();
        appMetricRegistry.counter("appDummyCounter3").inc();

        validateMetricCount("base, vendor, application", 6);
        validateMetricCount("base", 1);
        validateMetricCount("vendor", 2);
        validateMetricCount("application", 3);
        validateMetricCount("base, vendor", 3);
    }

    @Test
    public void testMetricScope() {
        mockPostMetricDataAndGetTestMetricCount();

        baseMetricRegistry.counter("baseDummyCounter1").inc();

        vendorMetricRegistry.counter("vendorDummyCounter1").inc();
        vendorMetricRegistry.counter("vendorDummyCounter2").inc();

        appMetricRegistry.counter("appDummyCounter1").inc();
        appMetricRegistry.counter("appDummyCounter2").inc();
        appMetricRegistry.counter("appDummyCounter3").inc();

        validateMetricCount(new String[]{}, 6);
        validateMetricCount(new String[]{Type.BASE.getName(), Type.VENDOR.getName(), Type.APPLICATION.getName()}, 6);
        validateMetricCount(new String[]{Type.BASE.getName()}, 1);
        validateMetricCount(new String[]{Type.VENDOR.getName()}, 2);
        validateMetricCount(new String[]{Type.APPLICATION.getName()}, 3);
        validateMetricCount(new String[]{"base", "vendor", "application"}, 6);
        validateMetricCount(new String[]{"base"}, 1);
        validateMetricCount(new String[]{"vendor"}, 2);
        validateMetricCount(new String[]{"application"}, 3);
    }

    @Test
    public void testDisableMetrics() {
        mockPostMetricDataAndGetTestMetricCount();

        baseMetricRegistry.counter("baseDummyCounter1").inc();
        vendorMetricRegistry.counter("vendorDummyCounter1").inc();
        appMetricRegistry.counter("appDummyCounter1").inc();

        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .namespace("namespace")
                .compartmentId("compartmentId")
                .resourceGroup("resourceGroup")
                .initialDelay(50L)
                .delay(20000L)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .descriptionEnabled(false)
                .monitoringClient(monitoringClient)
                .enabled(false);

        testMetricCount = 0;
        Routing routing = createRouting(ociMetricsSupportBuilder);
        WebServer webServer = createWebServer(routing);

        delay(1000L);

        // metric count should remain 0 as metrics is disabled
        assertThat(testMetricCount, is(equalTo(0)));
        webServer.shutdown();

    }

    private void mockPostMetricDataAndGetTestMetricCount() {
        // mock monitoringClient.postMetricData()
        doAnswer(invocationOnMock -> {
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            testMetricCount = postMetricDataDetails.getMetricData().size();
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());
    }

    private WebServer createWebServer(Routing routing) {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .port(8888)
                .routing(routing)
                .build();
        webServer.start();
        return webServer;
    }

    private Routing createRouting(OciMetricsSupport.Builder ociMetricsSupportBuilder) {
        Routing routing = Routing.builder()
                .register("/test", rules -> rules.put((req, res) -> {
                    res.send();
                }))
                .register(ociMetricsSupportBuilder)
                .build();
        return routing;
    }

    private void validateMetricCount(String scopesList, int expectedMetricCount) {
        Config config = Config.just(ConfigSources.create(Map.of(
                "compartmentId", "compartmentId",
                "namespace", "namespace",
                "resourceGroup", "resourceGroup",
                "initialDelay", "50",
                "delay", "15000",
                "schedulingTimeUnit", "milliseconds",
                "scopes", scopesList)
        ));
        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .config(config)
                .monitoringClient(monitoringClient);

        validateMetricCount(ociMetricsSupportBuilder, expectedMetricCount);
    }

    private void validateMetricCount(String[] scopes, int expectedMetricCount) {
        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .namespace("namespace")
                .compartmentId("compartmentId")
                .resourceGroup("resourceGroup")
                .initialDelay(50L)
                .delay(20000L)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .descriptionEnabled(false)
                .scopes(scopes)
                .monitoringClient(monitoringClient);

        validateMetricCount(ociMetricsSupportBuilder, expectedMetricCount);
    }

    private void validateMetricCount(OciMetricsSupport.Builder ociMetricsSupportBuilder, int expectedMetricCount) {
        testMetricCount = 0;
        Routing routing = createRouting(ociMetricsSupportBuilder);
        WebServer webServer = createWebServer(routing);

        Timer timer = new Timer(10);
        while (testMetricCount <= 0) {
            if (timer.expired()) {
                fail(String.format("Timed out after %d sec. waiting for testMetricCount",
                        timer.getTimeout()));
            }
            delay(50L);
        }

        assertThat(testMetricCount, is(equalTo(expectedMetricCount)));
        webServer.shutdown();
    }

    private static class Timer {
        private final long endTime;
        private final int timeOut;

        public Timer(int timeOut) {
            this.timeOut = timeOut;
            this.endTime = System.currentTimeMillis() + 1_000L * timeOut;
        }

        public boolean expired() {
            return System.currentTimeMillis() >= this.endTime;
        }

        int getTimeout() {
            return this.timeOut;
        }
    }

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignore) {
        }
    }
}
