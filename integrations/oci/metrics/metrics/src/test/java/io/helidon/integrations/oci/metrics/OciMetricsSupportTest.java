/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
package io.helidon.integrations.oci.metrics;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.metrics.api.RegistryFactory;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import com.oracle.bmc.monitoring.Monitoring;
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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class OciMetricsSupportTest {
    private final OciMetricsSupport.NameFormatter nameFormatter = new OciMetricsSupport.NameFormatter() { };
    private static final Monitoring monitoringClient = mock(Monitoring.class);
    private final Type[] types = {Type.BASE, Type.VENDOR, Type.APPLICATION};

    private static volatile Double[] testMetricUpdateCounterValue = new Double[2];
    private static volatile int testMetricCount = 0;
    // Use CountDownLatch to signal when to start testing, for example, test only after results has been retrieved.
    private static CountDownLatch countDownLatch1;
    private static int noOfExecutions;
    private static int noOfMetrics;

    private final RegistryFactory rf = RegistryFactory.getInstance();
    private final MetricRegistry baseMetricRegistry = rf.getRegistry(Type.BASE);
    private final MetricRegistry vendorMetricRegistry = rf.getRegistry(Type.VENDOR);
    private final MetricRegistry appMetricRegistry = rf.getRegistry(Type.APPLICATION);
    private static String endPoint = "https://telemetry.DummyEndpoint.com";
    private static String postingEndPoint;

    @BeforeAll
    static void mockSetGetEndpoints() {
        doAnswer(invocation -> {
            endPoint = invocation.getArgument(0);
            return null;
        }).when(monitoringClient).setEndpoint(any());
        doAnswer(invocation -> {
            return endPoint;
        }).when(monitoringClient).getEndpoint();
    }

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
    public void testMetricUpdate() throws InterruptedException {
        Counter counter = baseMetricRegistry.counter("DummyCounter");

        countDownLatch1 = new CountDownLatch(1);
        noOfExecutions = 0;

        doAnswer(invocationOnMock -> {
            noOfExecutions++;
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            List<MetricDataDetails> allMetricDataDetails = postMetricDataDetails.getMetricData();
            // put 1st result in testMetricUpdateCounterValue index 0 and succeeding update in index 1 to ensure
            // that the 1st update result does not overwrite the 2nd update in rare situations where all metric
            // updates have already completed before the process to assert results has even started
            testMetricUpdateCounterValue[noOfExecutions == 1 ? 0 : 1] =
                    allMetricDataDetails.get(0).getDatapoints().get(0).getValue();
            if (noOfExecutions == 1) {
                counter.inc();
            } else {
                // Give signal that multiple metric updates have been triggered
                countDownLatch1.countDown();
            }
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());

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

        // Wait for metric updates to complete
        countDownLatch1.await(10, java.util.concurrent.TimeUnit.SECONDS);

        // Test the 1st and 2nd metric counter updates
        assertThat(testMetricUpdateCounterValue[0].intValue(), is(equalTo(1)));
        assertThat(testMetricUpdateCounterValue[1].intValue(), is(equalTo(2)));

        webServer.shutdown().await(10, java.util.concurrent.TimeUnit.SECONDS);
    }

    @Test
    public void testEndpoint() throws InterruptedException {
        String originalEndPoint = endPoint;

        baseMetricRegistry.counter("baseDummyCounter1").inc();
        vendorMetricRegistry.counter("vendorDummyCounter1").inc();
        appMetricRegistry.counter("appDummyCounter1").inc();

        countDownLatch1 = new CountDownLatch(1);
        noOfExecutions = 0;

        doAnswer(invocationOnMock -> {
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            postingEndPoint = monitoringClient.getEndpoint();
            // Give signal that metrics has been posted
            countDownLatch1.countDown();
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());

        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .compartmentId("compartmentId")
                .namespace("namespace")
                .resourceGroup("resourceGroup")
                .initialDelay(0L)
                .delay(20L)
                .descriptionEnabled(false)
                .monitoringClient(monitoringClient);

        Routing routing = createRouting(ociMetricsSupportBuilder);

        WebServer webServer = createWebServer(routing);

        // Wait for metrics to be posted
        countDownLatch1.await(10, java.util.concurrent.TimeUnit.SECONDS);

        // Verify that telemetry-ingestion endpoint is properly set during postin
        assertThat(postingEndPoint, startsWith("https://telemetry-ingestion."));
        // Verify that original endpoint is restored after metric posting
        assertThat(monitoringClient.getEndpoint(), is(equalTo(originalEndPoint)));
        webServer.shutdown().await(10, java.util.concurrent.TimeUnit.SECONDS);
    }


    @Test
    public void testBatchSize() throws InterruptedException {
        baseMetricRegistry.counter("baseDummyCounter1").inc();
        baseMetricRegistry.counter("baseDummyCounter2").inc();
        baseMetricRegistry.counter("baseDummyCounter3").inc();

        vendorMetricRegistry.counter("vendorDummyCounter1").inc();
        vendorMetricRegistry.counter("vendorDummyCounter2").inc();
        vendorMetricRegistry.counter("vendorDummyCounter3").inc();

        appMetricRegistry.counter("appDummyCounter1").inc();
        appMetricRegistry.counter("appDummyCounter2").inc();
        appMetricRegistry.counter("appDummyCounter3").inc();
        appMetricRegistry.counter("appDummyCounter4").inc();

        // Should be 10 metrics
        int totalMetrics =
                baseMetricRegistry.getMetrics().size() +
                        vendorMetricRegistry.getMetrics().size() +
                        appMetricRegistry.getMetrics().size();

        int batchSize = 3;
        long batchDelay = 100L;
        // Should be 1
        int remainder = totalMetrics % batchSize;
        // Should be 4
        int noOfBatches = Math.round(totalMetrics / batchSize) + (remainder > 0 ? 1 : 0);

        countDownLatch1 = new CountDownLatch(1);
        noOfExecutions = 0;
        noOfMetrics = 0;

        doAnswer(invocationOnMock -> {
            noOfExecutions++;
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            int metricSizeToPost = postMetricDataDetails.getMetricData().size();
            noOfMetrics += metricSizeToPost;
            // Give signal that the last remaining metric in the last batch has been posted
            if (metricSizeToPost == remainder) {
                countDownLatch1.countDown();
            }
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());

        OciMetricsSupport.Builder ociMetricsSupportBuilder = OciMetricsSupport.builder()
                .compartmentId("compartmentId")
                .namespace("namespace")
                .resourceGroup("resourceGroup")
                .initialDelay(0L)
                .delay(20000L)
                .batchDelay(batchDelay)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .descriptionEnabled(false)
                .batchSize(batchSize)
                .monitoringClient(monitoringClient);


        Instant start = Instant.now();

        Routing routing = createRouting(ociMetricsSupportBuilder);

        WebServer webServer = createWebServer(routing);

        // Wait for last batch to be completed
        countDownLatch1.await(10, java.util.concurrent.TimeUnit.SECONDS);
        Instant finish = Instant.now();

        // Batch size of 3 for 10 metrics should yield 4 batches to post
        assertThat(totalMetrics, is(10));
        assertThat(noOfBatches, is(4));
        assertThat(noOfExecutions, is(noOfBatches));
        assertThat(noOfMetrics, is(totalMetrics));

        // Last batch is not delayed so compute only the delays for prior batches
        long estimatedTotalBatchDelay = batchDelay * (noOfBatches - 1);
        // Test total batch delay
        assertThat(Duration.between(start, finish).toMillis(), is(greaterThanOrEqualTo(estimatedTotalBatchDelay)));

        webServer.shutdown().await(10, java.util.concurrent.TimeUnit.SECONDS);
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
                .delay(500L)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .descriptionEnabled(false)
                .monitoringClient(monitoringClient)
                .enabled(false);

        testMetricCount = 0;
        Routing routing = createRouting(ociMetricsSupportBuilder);
        WebServer webServer = createWebServer(routing);

        delay(1000L);

        webServer.shutdown().await(10, java.util.concurrent.TimeUnit.SECONDS);
        // metric count should remain 0 as metrics is disabled
        assertThat(testMetricCount, is(equalTo(0)));
    }

    private void mockPostMetricDataAndGetTestMetricCount() {
        // mock monitoringClient.postMetricData()
        doAnswer(invocationOnMock -> {
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            testMetricCount = postMetricDataDetails.getMetricData().size();
            // Give signal that testMetricCount was retrieved
            countDownLatch1.countDown();
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());
    }

    private WebServer createWebServer(Routing routing) {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .port(8888)
                .addRouting(routing)
                .build();
        webServer.start().await(10L, TimeUnit.SECONDS);
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
                .delay(2000L)
                .schedulingTimeUnit(TimeUnit.MILLISECONDS)
                .descriptionEnabled(false)
                .scopes(scopes)
                .monitoringClient(monitoringClient);

        validateMetricCount(ociMetricsSupportBuilder, expectedMetricCount);
    }

    private void validateMetricCount(OciMetricsSupport.Builder ociMetricsSupportBuilder, int expectedMetricCount) {
        testMetricCount = 0;
        countDownLatch1 = new CountDownLatch(1);
        Routing routing = createRouting(ociMetricsSupportBuilder);
        WebServer webServer = createWebServer(routing);

        try {
            // Wait for signal from metric update that testMetricCount has been retrieved
            countDownLatch1.await(10, TimeUnit.SECONDS);
        } catch(InterruptedException e) {
            fail("Error while waiting for testMetricCount: " + e.getMessage());
        }
        webServer.shutdown().await(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(testMetricCount, is(equalTo(expectedMetricCount)));
    }

    private static void delay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            fail("InterruptedException received in delay(" + millis + ") with message: " + ie.getMessage());
        }
    }
}
