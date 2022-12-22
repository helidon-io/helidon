/*
 * Copyright (c) 2018, 2022 Oracle and/or its affiliates.
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

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.config.testing.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.microprofile.metrics.HelloWorldResource.MESSAGE_SIMPLE_TIMER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Class HelloWorldTest.
 */
@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
public class HelloWorldTest {

    @Inject
    WebTarget webTarget;

    @Inject
    MetricRegistry registry;

    @Inject
    @RegistryType(type = MetricRegistry.Type.BASE)
    MetricRegistry restRequestMetricsRegistry;

    @BeforeAll
    public static void initialize() {
        System.setProperty("jersey.config.server.logging.verbosity", "FINE");
    }

    @AfterAll
    public static void cleanup() {
        MetricsMpServiceTest.wrapupTest();
    }

    // Gives the server a chance to update metrics after sending the response before the test code
    // checks those metrics.
    static <T> T runAndPause(Callable<T> c) throws Exception {
        T result = c.call();
        pause();
        return result;
    }

    static void pause() {
        try {
            TimeUnit.MILLISECONDS.sleep(500L);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    public void registerCounter() {
        MetricsMpServiceTest.registerCounter(registry, "helloCounter");
    }

    @Test
    public void testMetrics() throws InterruptedException {
        final int iterations = 1;
        Counter classLevelCounterForConstructor =
                registry.getCounters().get(new MetricID(
                        HelloWorldResource.class.getName() + "." + HelloWorldResource.class.getSimpleName()));
        long classLevelCounterStart = classLevelCounterForConstructor.getCount();

        IntStream.range(0, iterations).forEach(
                i -> webTarget
                        .path("helloworld")
                        .request()
                        .accept(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));
        pause();
        assertThat("Value of explicitly-updated counter", registry.counter("helloCounter").getCount(),
                is((long) iterations));

        assertThatWithRetry("Diff in value of interceptor-updated class-level counter for constructor",
                            () -> classLevelCounterForConstructor.getCount() - classLevelCounterStart,
                            is((long) iterations));

        Counter classLevelCounterForMethod =
                registry.getCounters().get(new MetricID(HelloWorldResource.class.getName() + ".message"));
        assertThat("Class-declared counter metric for constructor", classLevelCounterForMethod, is(notNullValue()));
        assertThat("Value of interceptor-updated class-level counter for method", classLevelCounterForMethod.getCount(),
                is((long) iterations));

        SimpleTimer simpleTimer = getSyntheticSimpleTimer("message");
        assertThat("Synthetic simple timer", simpleTimer, is(notNullValue()));
        assertThatWithRetry("Synthetic simple timer count value", simpleTimer::getCount, is((long) iterations));

        checkMetricsUrl(iterations);
    }

    @Test
    public void testSyntheticSimpleTimer() throws InterruptedException {
        testSyntheticSimpleTimer(1L);
    }

    @Test
    public void testMappedException() throws Exception {
        Tag[] tags = new Tag[] {new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "triggerMappedException")};
        SimpleTimer simpleTimer = restRequestMetricsRegistry.simpleTimer("REST.request", tags);
        Counter counter = restRequestMetricsRegistry.counter("REST.request.unmappedException.total", tags);

        long successfulBeforeRequest = simpleTimer.getCount();
        long unsuccessfulBeforeRequest = counter.getCount();

        Response response = runAndPause(() -> webTarget.path("helloworld/mappedExc")
                .request()
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get()
        );

        assertThat("Response code from mapped exception endpoint", response.getStatus(), is(500));
        assertThatWithRetry("Change in successful count",
                            () -> simpleTimer.getCount() - successfulBeforeRequest,
                            is(1L));
        assertThat("Change in unsuccessful count", counter.getCount() - unsuccessfulBeforeRequest, is(0L));
    }

    @Test
    void testUnmappedException() throws Exception {
        Tag[] tags = new Tag[] {new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "triggerUnmappedException")};
        SimpleTimer simpleTimer = restRequestMetricsRegistry.simpleTimer("REST.request", tags);
        Counter counter = restRequestMetricsRegistry.counter("REST.request.unmappedException.total", tags);

        long successfulBeforeRequest = simpleTimer.getCount();
        long unsuccessfulBeforeRequest = counter.getCount();

        Response response = runAndPause(() -> webTarget.path("helloworld/unmappedExc")
                .request()
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get()
        );

        assertThat("Response code from unmapped exception endpoint", response.getStatus(), is(500));
        assertThatWithRetry("Change in successful count",
                            () -> simpleTimer.getCount() - successfulBeforeRequest,
                            is(0L));
        assertThat("Change in unsuccessful count", counter.getCount() - unsuccessfulBeforeRequest, is(1L));
    }

    void testSyntheticSimpleTimer(long expectedSyntheticSimpleTimerCount) {
        SimpleTimer explicitSimpleTimer = registry.getSimpleTimer(new MetricID(MESSAGE_SIMPLE_TIMER));
        assertThat("SimpleTimer from explicit @SimplyTimed", explicitSimpleTimer, is(notNullValue()));
        SimpleTimer syntheticSimpleTimer = getSyntheticSimpleTimer("messageWithArg", String.class);
        assertThat("SimpleTimer from @SyntheticRestRequest", syntheticSimpleTimer, is(notNullValue()));
        IntStream.range(0, (int) expectedSyntheticSimpleTimerCount).forEach(
                i -> webTarget
                        .path("helloworld/withArg/Joe")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        pause();
        assertThatWithRetry("SimpleTimer from explicit @SimpleTimed count",
                            explicitSimpleTimer::getCount,
                            is(expectedSyntheticSimpleTimerCount));

        assertThatWithRetry("SimpleTimer from @SyntheticRestRequest count",
                            syntheticSimpleTimer::getCount,
                            is(expectedSyntheticSimpleTimerCount));
    }

    SimpleTimer getSyntheticSimpleTimer(String methodName, Class<?>... paramTypes) {
        try {
            return getSyntheticSimpleTimer(HelloWorldResource.class.getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    SimpleTimer getSyntheticSimpleTimer(Method method) {
            return getSyntheticSimpleTimer(MetricsCdiExtension.restEndpointSimpleTimerMetricID(method));
    }

    SimpleTimer getSyntheticSimpleTimer(MetricID metricID) {

        Map<MetricID, SimpleTimer> simpleTimers = restRequestMetricsRegistry.getSimpleTimers();
        return simpleTimers.get(metricID);
    }

    void checkMetricsUrl(int iterations) {
        assertThatWithRetry("helloCounter count", () -> {
            JsonObject app = webTarget
                    .path("metrics")
                    .request()
                    .accept(MediaType.APPLICATION_JSON_TYPE)
                    .get(JsonObject.class)
                    .getJsonObject("application");
            return app.getJsonNumber("helloCounter").intValue();
        }, is(iterations));
    }
}
