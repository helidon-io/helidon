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
package io.helidon.microprofile.metrics;

import java.time.Duration;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.metrics.api.MetricsConfig.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_MESSAGE_SIMPLE_TIMER;
import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_MESSAGE_TIMER;
import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_RESPONSE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
@AddConfig(key =
        "metrics."
                + KEY_PERFORMANCE_INDICATORS_CONFIG_KEY
                + ".extended",
        value = "true")
@AddConfig(key = "metrics.permit-all", value = "true")
public class HelloWorldAsyncResponseTest {

    @Inject
    WebTarget webTarget;

    @Inject
    MetricRegistry registry;

    // TODO change to RegistryScope once MP makes it a qualifier
    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry syntheticTimerRegistry;

    // TODO change to RegistryScope once MP makes it a qualifier
    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    @Disabled
    @Test
    public void test() throws Exception {
        MetricID metricID = MetricsCdiExtension
                .restEndpointTimerMetricID(HelloWorldResource.class.getMethod("slowMessage",
                                                                              AsyncResponse.class,
                                                                              ServerResponse.class));

        SortedMap<MetricID, Timer> timers = registry.getTimers();

        Timer explicitTimer = timers.get(new MetricID(SLOW_MESSAGE_SIMPLE_TIMER));
        assertThat("Timer for explicit annotation", explicitTimer, is(notNullValue()));
        long explicitTimerCountBefore = explicitTimer.getCount();
        Duration explicitSimpleTimerDurationBefore = explicitTimer.getElapsedTime();

        timers = syntheticTimerRegistry.getTimers();
        Timer timer = timers.get(metricID);
        assertThat("Synthetic Timer for the endpoint", timer, is(notNullValue()));
        long syntheticTimerCountBefore = timer.getCount();
        Duration syntheticTimerDurationBefore = timer.getElapsedTime();

        timers = registry.getTimers();
        Timer slowMessageTimer = timers.get(new MetricID(SLOW_MESSAGE_TIMER));
        assertThat("Timer", slowMessageTimer, is(notNullValue()));
        long slowMessageTimerCountBefore= slowMessageTimer.getCount();

        String result = HelloWorldTest.runAndPause(() ->webTarget
                .path("helloworld/slow")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class)
        );

        /*
         * We test timers (explicit and the implicit REST.request one) including on the async method.
         */
        assertThat("Mismatched string result", result, is(HelloWorldResource.SLOW_RESPONSE));

        Duration minDuration = Duration.ofMillis(HelloWorldResource.SLOW_DELAY_MS);

        assertThatWithRetry("Change in count for explicit SimpleTimer",
                            () -> explicitTimer.getCount() - explicitTimerCountBefore,
                            is(1L));
        long explicitDiffNanos = explicitTimer.getElapsedTime().toNanos() - explicitSimpleTimerDurationBefore.toNanos();
        assertThat("Change in elapsed time for explicit SimpleTimer", explicitDiffNanos, is(greaterThan(minDuration.toNanos())));

        assertThatWithRetry("Change in synthetic SimpleTimer elapsed time",
                            () -> slowMessageTimer.getElapsedTime().toNanos() - syntheticTimerDurationBefore.toNanos(),
                            is(greaterThan(minDuration.toNanos())));

        assertThat("Change in timer count", slowMessageTimer.getCount() - slowMessageTimerCountBefore, is(1L));
        assertThat("Timer mean rate", slowMessageTimer.getSnapshot().getMean(), is(greaterThan(0.0D)));
    }

    @Test
    public void testAsyncWithArg() {
        LongStream.range(0, 3).forEach(
                i -> webTarget
                        .path("helloworld/slowWithArg/Johan")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        Timer syntheticTimer = getSyntheticTimer();
        // Give the server a chance to update the metrics, which happens just after it sends each response.
        assertThatWithRetry("Synthetic Timer count", syntheticTimer::getCount, is(3L));
    }

    Timer getSyntheticTimer() {
        MetricID metricID = null;
        try {
            metricID = MetricsCdiExtension.restEndpointTimerMetricID(
                    HelloWorldResource.class.getMethod("slowMessageWithArg",
                    String.class, AsyncResponse.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        SortedMap<MetricID, Timer> timers = syntheticTimerRegistry.getTimers();
        Timer syntheticTimer = timers.get(metricID);
        // We should not need to retry here. Annotation processing creates the synthetic simple timers long before tests run.
        assertThat("Synthetic simple timer "
                        + MetricsCdiExtension.SYNTHETIC_TIMER_METRIC_NAME,
                syntheticTimer, is(notNullValue()));
        return syntheticTimer;
    }

    @Test
    void testInflightRequests() throws InterruptedException, ExecutionException {
        assertThat("In-flight requests ConcurrentGauge is present", HelloWorldResource.inflightRequests(vendorRegistry).isPresent(),
                is(true));

        int testCount = Integer.getInteger("helidon.microprofile.metrics.asyncRepeatCount", 1);
        for (int i = 0; i < testCount; i++) {
            long beforeRequest = inflightRequestsCount();
            HelloWorldResource.initSlowRequest();

            // The request processing will start but then stall, waiting until after this test fetches the "duringRequest"
            // value of inflightRequests.
            Future<String> future =
                    webTarget
                            .path("helloworld/slow")
                            .request(MediaType.TEXT_PLAIN_TYPE)
                            .async()
                            .get(String.class);

            HelloWorldResource.awaitSlowRequestStarted();
            long duringRequest = inflightRequestsCount();

            HelloWorldResource.reportDuringRequestFetched();

            String response = future.get();

            // The response might arrive here before the server-side code which updates the inflight metric has run. So wait.
            HelloWorldResource.awaitResponseSent();

            assertThat("Slow response", response, containsString(SLOW_RESPONSE));
            assertThat("Change in inflight from before (" + beforeRequest + ") to during (" + duringRequest
                            + ") the slow request", duringRequest - beforeRequest, is(1L));
            // The update to the in-flight count occurs after the response completes, so retry a bit until the value returns to 0.
            assertThatWithRetry("Change in inflight from before (" + beforeRequest + ") to after the slow request",
                                () -> inflightRequestsCount() - beforeRequest,
                                is(0L));
        }
    }

    private long inflightRequestsCount() {
        return HelloWorldResource.inflightRequestsCount(vendorRegistry);
    }

    @Test
    void testBasicPerRequestMetrics() {
        TestBasicPerformanceIndicators.doCheckMetricsVendorURL(webTarget);
    }

}
