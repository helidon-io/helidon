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
 *
 */
package io.helidon.microprofile.metrics;


import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import javax.inject.Inject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.MediaType;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.ConcurrentGauge;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_MESSAGE_SIMPLE_TIMER;
import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_MESSAGE_TIMER;
import static io.helidon.microprofile.metrics.HelloWorldResource.SLOW_RESPONSE;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsConfig.Builder.KEY_PERFORMANCE_INDICATORS_CONFIG_KEY;
import static io.helidon.metrics.KeyPerformanceIndicatorMetricsConfig.Builder.KEY_PERFORMANCE_INDICATORS_EXTENDED_CONFIG_KEY;

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
    public void test() throws NoSuchMethodException {
        MetricID metricID = MetricsCdiExtension.syntheticSimpleTimerMetricID(HelloWorldResource.class.getMethod("slowMessage",
                AsyncResponse.class));

        SortedMap<MetricID, SimpleTimer> simpleTimers = registry.getSimpleTimers();

        SimpleTimer explicitSimpleTimer = simpleTimers.get(new MetricID(SLOW_MESSAGE_SIMPLE_TIMER));
        assertThat("SimpleTimer for explicit annotation", explicitSimpleTimer, is(notNullValue()));
        long explicitSimpleTimerCountBefore = explicitSimpleTimer.getCount();
        Duration explicitSimpleTimerDurationBefore = explicitSimpleTimer.getElapsedTime();

        simpleTimers = syntheticSimpleTimerRegistry.getSimpleTimers();
        SimpleTimer simpleTimer = simpleTimers.get(metricID);
        assertThat("Synthetic SimpleTimer for the endpoint", simpleTimer, is(notNullValue()));
        long syntheticSimpleTimerCountBefore = simpleTimer.getCount();
        Duration syntheticaSimpleTimerDurationBefore = simpleTimer.getElapsedTime();

        Map<MetricID, Timer> timers = registry.getTimers();
        Timer timer = timers.get(new MetricID(SLOW_MESSAGE_TIMER));
        assertThat("Timer", timer, is(notNullValue()));
        long slowMessageTimerCountBefore= timer.getCount();

        String result = webTarget
                .path("helloworld/slow")
                .request()
                .accept(MediaType.TEXT_PLAIN)
                .get(String.class);

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

        Duration minDuration = Duration.ofSeconds(HelloWorldResource.SLOW_DELAY_SECS);

        assertThat("Change in count for explicit SimpleTimer",
                explicitSimpleTimer.getCount() - explicitSimpleTimerCountBefore, is(1L));
        long explicitDiffNanos = explicitSimpleTimer.getElapsedTime().toNanos() - explicitSimpleTimerDurationBefore.toNanos();
        assertThat("Change in elapsed time for explicit SimpleTimer", explicitDiffNanos, is(greaterThan(minDuration.toNanos())));

        long syntheticDiffNanos = simpleTimer.getElapsedTime().toNanos() - syntheticaSimpleTimerDurationBefore.toNanos();
        assertThat("Change in synthetic SimpleTimer elapsed time", syntheticDiffNanos,
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
        assertThat("Synthetic SimpleTimer count", syntheticSimpleTimer.getCount(), is(3L));
    }

    SimpleTimer getSyntheticSimpleTimer() {
        MetricID metricID = null;
        try {
            metricID = MetricsCdiExtension.syntheticSimpleTimerMetricID(HelloWorldResource.class.getMethod("slowMessageWithArg",
                    String.class, AsyncResponse.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        SortedMap<MetricID, SimpleTimer> simpleTimers = syntheticSimpleTimerRegistry.getSimpleTimers();
        SimpleTimer syntheticSimpleTimer = simpleTimers.get(metricID);
        assertThat("Synthetic simple timer "
                        + MetricsCdiExtension.SYNTHETIC_SIMPLE_TIMER_METRIC_NAME,
                syntheticSimpleTimer, is(notNullValue()));
        return syntheticSimpleTimer;
    }

    @Test
    void testInflightRequests() throws InterruptedException, ExecutionException {
        Optional<ConcurrentGauge> inflightRequests =
                vendorRegistry.getConcurrentGauges((metricID, metric) -> metricID.getName().endsWith("in-flight"))
                        .values()
                        .stream()
                        .findAny();
        assertThat("In-flight requests ConcurrentGauge is present", inflightRequests.isPresent(),is(true));

        long beforeRequest = inflightRequests.get().getCount();
        HelloWorldResource.initSlowRequest();

        Future<String> future =
            webTarget
                .path("helloworld/slow")
                .request(MediaType.TEXT_PLAIN_TYPE)
                .async()
                .get(String.class);
        HelloWorldResource.awaitSlowRequestStarted();
        long duringRequest = inflightRequests.get().getCount();
        String response = future.get();
        long afterRequest = inflightRequests.get().getCount();

        assertThat("Slow response", response, containsString(SLOW_RESPONSE));
        assertThat("Change in inflight during slow request", duringRequest - beforeRequest, is(1L));
        assertThat("Change in inflight after slow request completes", afterRequest - beforeRequest, is(0L));
    }

    @Test
    void testBasicPerRequestMetrics() {
        TestBasicPerformanceIndicators.doCheckMetricsVendorURL(webTarget);
    }
}
