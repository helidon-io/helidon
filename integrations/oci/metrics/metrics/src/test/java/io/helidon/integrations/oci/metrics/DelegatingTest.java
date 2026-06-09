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

package io.helidon.integrations.oci.metrics;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.metrics.api.Counter;
import io.helidon.metrics.api.Meter;
import io.helidon.metrics.api.MeterRegistry;
import io.helidon.metrics.api.Metrics;
import io.helidon.metrics.api.MetricsConfig;
import io.helidon.metrics.api.MetricsFactory;
import io.helidon.service.registry.Services;
import io.helidon.testing.junit5.Testing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;

import com.oracle.bmc.monitoring.Monitoring;
import com.oracle.bmc.monitoring.model.MetricDataDetails;
import com.oracle.bmc.monitoring.model.PostMetricDataDetails;
import com.oracle.bmc.monitoring.requests.PostMetricDataRequest;
import com.oracle.bmc.monitoring.responses.PostMetricDataResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@Testing.Test
class DelegatingTest  {

    private static final Monitoring monitoringClient = mock(Monitoring.class);
    private static final Double[] testMetricUpdateCounterValue = new Double[2];

    private static String endPoint = "https://telemetry.DummyEndpoint.com";

    private static MeterRegistry meterRegistry;

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

    @BeforeAll
    static void prepareMeterRegistry() {
        // Save the mocked monitorincClient so the OCI metrics publisher will get that one when it
        // asks the service registry for it.
        Services.set(Monitoring.class, monitoringClient);

        var config = Config.just("""
                                     metrics:
                                           publishers:
                                             - type: oci
                                               fleet: my-fleet
                                               project: my-project
                                               initial-delay: PT0.5S
                                               delay: PT1S
                                             - type: prometheus
                                     """, MediaTypes.APPLICATION_YAML);
        meterRegistry = MetricsFactory.getInstance().globalRegistry(MetricsConfig.builder()
                                                                            .config(config.get("metrics"))
                                                                            .build());
    }

    @BeforeEach
    void resetState() {
        // clear all registry
        meterRegistry.meters()
                .forEach(meterRegistry::remove);

        endPoint = "https://telemetry.DummyEndpoint.com";
    }

    @Test
    void testOverriddenProperties() {
//        var meterRegistry = Metrics.globalRegistry();
        Counter counter = meterRegistry.getOrCreate(Counter.builder("DummyCounter")
                                                            .scope(Meter.Scope.BASE));

        CountDownLatch countDownLatch = new CountDownLatch(1);
        var noOfExecutions = new AtomicInteger(0);
        var namespace = new AtomicReference<String>();
        var resourceGroup = new AtomicReference<String>();

        doAnswer(invocationOnMock -> {
            noOfExecutions.incrementAndGet();
            PostMetricDataRequest postMetricDataRequest = invocationOnMock.getArgument(0);
            PostMetricDataDetails postMetricDataDetails = postMetricDataRequest.getPostMetricDataDetails();
            List<MetricDataDetails> allMetricDataDetails = postMetricDataDetails.getMetricData();
            // put 1st result in testMetricUpdateCounterValue index 0 and succeeding update in index 1 to ensure
            // that the 1st update result does not overwrite the 2nd update in rare situations where all metric
            // updates have already completed before the process to assert results has even started
            testMetricUpdateCounterValue[noOfExecutions.get() == 1 ? 0 : 1] =
                    allMetricDataDetails.getFirst().getDatapoints().getFirst().getValue();
            if (noOfExecutions.get() == 1) {
                counter.increment();
            } else {
                // Give signal that multiple metric updates have been triggered
                countDownLatch.countDown();
            }
            namespace.set(allMetricDataDetails.getFirst().getNamespace());
            resourceGroup.set(allMetricDataDetails.getFirst().getResourceGroup());
            return PostMetricDataResponse.builder()
                    .__httpStatusCode__(200)
                    .build();
        }).when(monitoringClient).postMetricData(any());


            HttpRouting.Builder routing = createRouting();

            counter.increment();
            WebServer webServer = createWebServer(routing);

            // Wait for metric updates to complete
        try {
            countDownLatchWait(countDownLatch);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // Test the 1st and 2nd metric counter updates
            assertThat(testMetricUpdateCounterValue[0].intValue(), is(equalTo(1)));
            assertThat(testMetricUpdateCounterValue[1].intValue(), is(equalTo(2)));
            assertThat(namespace.get(), is(equalTo("my-project")));
            assertThat(resourceGroup.get(), is(equalTo("my-fleet")));

            webServer.stop();
    }

    void countDownLatchWait(CountDownLatch countDownLatch) throws InterruptedException {
        if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
            fail("CountDownLatch timed out");
        }
    }
    private WebServer createWebServer(HttpRouting.Builder routing) {
        WebServer webServer = WebServer.builder()
                .host("localhost")
                .addRouting(routing)
                .build();
        webServer.start();
        return webServer;
    }

    private HttpRouting.Builder createRouting() {
        return HttpRouting.builder()
                .put("/test", (req, res) -> res.send());
    }

}
