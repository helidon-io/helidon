/*
 * Copyright (c) 2021, 2022 Oracle and/or its affiliates.
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
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.ServerResponse;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
import static io.helidon.metrics.api.KeyPerformanceIndicatorMetricsSettings.Builder.KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY;
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
                + "." + KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY,
        value = "true")
public class HelloWorldAsyncResponseTest {

    @Inject
    WebTarget webTarget;

    @Inject
    MetricRegistry registry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    private MetricRegistry syntheticSimpleTimerRegistry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.VENDOR)
    private MetricRegistry vendorRegistry;

    @Test
    public void test() throws Exception {
        MetricID metricID = MetricsCdiExtension
                .restEndpointSimpleTimerMetricID(HelloWorldResource.class.getMethod("slowMessage",
                                                                                    AsyncResponse.class,
                                                                                    ServerResponse.class));

        SortedMap<MetricID, SimpleTimer> simpleTimers = registry.getSimpleTimers();

        SimpleTimer explicitSimpleTimer = simpleTimers.get(new MetricID(SLOW_MESSAGE_SIMPLE_TIMER));
        assertThat("SimpleTimer for explicit annotation", explicitSimpleTimer, is(notNullValue()));
        long explicitSimpleTimerCountBefore = explicitSimpleTimer.getCount();
        Duration explicitSimpleTimerDurationBefore = explicitSimpleTimer.getElapsedTime();

        simpleTimers = syntheticSimpleTimerRegistry.getSimpleTimers();
        SimpleTimer simpleTimer = simpleTimers.get(metricID);
        assertThat("Synthetic SimpleTimer for the endpoint", simpleTimer, is(notNullValue()));
        Duration syntheticSimpleTimerDurationBefore = simpleTimer.getElapsedTime();

        Map<MetricID, Timer> timers = registry.getTimers();
        Timer timer = timers.get(new MetricID(SLOW_MESSAGE_TIMER));
        assertThat("Timer", timer, is(notNullValue()));
        long slowMessageTimerCountBefore= timer.getCount();

        String result = HelloWorldTest.runAndPause(() ->webTarget
                .path("helloworld/slow")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class)
        );

        /*
         * We test simple timers (explicit and the implicit REST.request one) and timers on the async method.
         *
         * We don't test a ConcurrentGauge, which is the other metric that has a post-invoke update, because it reports data
         * for the preceding whole minute. We don't want to deal with the timing issues to make sure that updates to the
         * metric fall within one minute and that we check it in the next minute. That's done,
         * though not for the JAX-RS async case, in the SE metrics tests. Because the async completion mechanism is independent
         * of the specific type of metric, we're not missing much by excluding a ConcurrentGauge from the async method.
         */
        assertThat("Mismatched string result", result, is(HelloWorldResource.SLOW_RESPONSE));

        Duration minDuration = Duration.ofMillis(HelloWorldResource.SLOW_DELAY_MS);

        assertThatWithRetry("Change in count for explicit SimpleTimer",
                            () -> explicitSimpleTimer.getCount() - explicitSimpleTimerCountBefore,
                            is(1L));
        long explicitDiffNanos = explicitSimpleTimer.getElapsedTime().toNanos() - explicitSimpleTimerDurationBefore.toNanos();
        assertThat("Change in elapsed time for explicit SimpleTimer", explicitDiffNanos, is(greaterThan(minDuration.toNanos())));

        // Helidon updates the synthetic simple timer after the response has been sent. It's possible that the server has not
        // done that update yet due to thread scheduling. So retry.
        assertThatWithRetry("Change in synthetic SimpleTimer elapsed time",
                            () -> simpleTimer.getElapsedTime().toNanos() - syntheticSimpleTimerDurationBefore.toNanos(),
                            is(greaterThan(minDuration.toNanos())));

        assertThat("Change in timer count", timer.getCount() - slowMessageTimerCountBefore, is(1L));
        assertThat("Timer mean rate", timer.getMeanRate(), is(greaterThan(0.0)));
    }

    @Test
    public void testAsyncWithArg() {
        LongStream.range(0, 3).forEach(
                i -> webTarget
                        .path("helloworld/slowWithArg/Johan")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        SimpleTimer syntheticSimpleTimer = getSyntheticSimpleTimer();
        // Give the server a chance to update the metrics, which happens just after it sends each response.
        assertThatWithRetry("Synthetic SimpleTimer count", syntheticSimpleTimer::getCount, is(3L));
    }

    SimpleTimer getSyntheticSimpleTimer() {
        MetricID metricID = null;
        try {
            metricID = MetricsCdiExtension.restEndpointSimpleTimerMetricID(
                    HelloWorldResource.class.getMethod("slowMessageWithArg",
                    String.class, AsyncResponse.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        SortedMap<MetricID, SimpleTimer> simpleTimers = syntheticSimpleTimerRegistry.getSimpleTimers();
        SimpleTimer syntheticSimpleTimer = simpleTimers.get(metricID);
        // We should not need to retry here. Annotation processing creates the synthetic simple timers long before tests run.
        assertThat("Synthetic simple timer "
                        + MetricsCdiExtension.SYNTHETIC_SIMPLE_TIMER_METRIC_NAME,
                syntheticSimpleTimer, is(notNullValue()));
        return syntheticSimpleTimer;
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
