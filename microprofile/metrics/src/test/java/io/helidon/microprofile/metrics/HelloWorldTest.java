/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.microprofile.metrics.HelloWorldResource.MESSAGE_TIMER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Class HelloWorldTest.
 */
@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
@AddConfig(key = "metrics.permit-all", value = "true")
class HelloWorldTest {

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

    private static void clearMetrics() {
        RegistryFactory.getInstance()
                .getRegistry(Registry.APPLICATION_SCOPE)
                .removeMatching(MetricFilter.ALL);
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
    public void testMetrics() {
        final int iterations = 1;
        Counter classLevelCounterForConstructor =
                registry.getCounters().get(new MetricID(
                        HelloWorldResource.class.getName() + "." + HelloWorldResource.class.getSimpleName()));
        long classLevelCounterStart = classLevelCounterForConstructor.getCount();
        Timer timer = getSyntheticTimer("message");
        long initialTimerCount = timer != null ? timer.getCount() : 0L;

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

        Timer timerAgain = getSyntheticTimer("message");
        assertThat("Synthetic timer", timer, is(notNullValue()));
        assertThatWithRetry("Synthetic timer count value",
                            () -> timerAgain.getCount() - initialTimerCount,
                            is((long) iterations));

        checkMetricsUrl(iterations);
    }

    @Test
    public void testSyntheticTimer() throws InterruptedException {
        testSyntheticTimer(1L);
    }

    @Test
    public void testMappedException() throws Exception {
        Tag[] tags = new Tag[] {new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "triggerMappedException")};
        Timer timer = restRequestMetricsRegistry.timer("REST.request", tags);
        Counter counter = restRequestMetricsRegistry.counter("REST.request.unmappedException.total", tags);

        long successfulBeforeRequest = timer.getCount();
        long unsuccessfulBeforeRequest = counter.getCount();

        Response response = runAndPause(() -> webTarget.path("helloworld/mappedExc")
                .request()
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get()
        );

        assertThat("Response code from mapped exception endpoint", response.getStatus(), is(500));
        assertThatWithRetry("Change in successful count",
                            () -> timer.getCount() - successfulBeforeRequest,
                            is(1L));
        assertThat("Change in unsuccessful count", counter.getCount() - unsuccessfulBeforeRequest, is(0L));
    }

    @Test
    @Disabled("CatchAllExceptionMapper was removed")
    void testUnmappedException() throws Exception {
        Tag[] tags = new Tag[] {new Tag("class", HelloWorldResource.class.getName()),
                new Tag("method", "triggerUnmappedException")};
        Timer timer = restRequestMetricsRegistry.timer("REST.request", tags);
        Counter counter = restRequestMetricsRegistry.counter("REST.request.unmappedException.total", tags);

        long successfulBeforeRequest = timer.getCount();
        long unsuccessfulBeforeRequest = counter.getCount();

        Response response = runAndPause(() -> webTarget.path("helloworld/unmappedExc")
                .request()
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get()
        );

        assertThat("Response code from unmapped exception endpoint", response.getStatus(), is(500));
        assertThatWithRetry("Change in successful count",
                            () -> timer.getCount() - successfulBeforeRequest,
                            is(0L));
        assertThat("Change in unsuccessful count", counter.getCount() - unsuccessfulBeforeRequest, is(1L));
    }

    void testSyntheticTimer(long iterationsToRun) {
        Timer explicitTimer = registry.getTimer(new MetricID(MESSAGE_TIMER));
        assertThat("SimpleTimer from explicit @SimplyTimed", explicitTimer, is(notNullValue()));
        Timer syntheticTimer = getSyntheticTimer("messageWithArg", String.class);
        assertThat("SimpleTimer from @SyntheticRestRequest", syntheticTimer, is(notNullValue()));
        long explicitTimerStartCount = explicitTimer.getCount();
        long syntheticTimerStartCount = syntheticTimer.getCount();
        IntStream.range(0, (int) iterationsToRun).forEach(
                i -> webTarget
                        .path("helloworld/withArg/Joe")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        pause();
        assertThatWithRetry("Change in SimpleTimer from explicit @SimpleTimed count",
                            () -> explicitTimer.getCount() - explicitTimerStartCount,
                            is(iterationsToRun));

        assertThatWithRetry("Change in SimpleTimer from @SyntheticRestRequest count",
                            () -> syntheticTimer.getCount() - syntheticTimerStartCount,
                            is(iterationsToRun));
    }

    Timer getSyntheticTimer(String methodName, Class<?>... paramTypes) {
        try {
            return getSyntheticTimer(HelloWorldResource.class.getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    Timer getSyntheticTimer(Method method) {
            return getSyntheticTimer(MetricsCdiExtension.restEndpointTimerMetricID(method));
    }

    Timer getSyntheticTimer(MetricID metricID) {

        Map<MetricID, Timer> timers = restRequestMetricsRegistry.getTimers();
        return timers.get(metricID);
    }

    void checkMetricsUrl(int iterations) {
        assertThatWithRetry("helloCounter count", () -> {
            String promOutput = webTarget
                    .path("metrics")
                    .request()
                    .accept(MediaType.TEXT_PLAIN)
                    .get(String.class);
            Pattern pattern = Pattern.compile(".*?^helloCounter_total\\S*\\s+(\\S+).*?", Pattern.MULTILINE | Pattern.DOTALL);
            Matcher matcher = pattern.matcher(promOutput);
            assertThat("Output matched pattern", matcher.matches(), is(true));
            assertThat("Matched output contains a capturing group for the count", matcher.groupCount(), is(1));
            return (int) Double.parseDouble(matcher.group(1));
        }, is(iterations));
    }
}
