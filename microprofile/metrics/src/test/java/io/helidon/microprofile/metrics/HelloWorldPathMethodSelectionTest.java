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

package io.helidon.microprofile.metrics;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import io.helidon.microprofile.server.CatchAllExceptionMapper;
import io.helidon.microprofile.testing.AddConfigBlock;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import org.eclipse.microprofile.metrics.MetricFilter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.MatcherWithRetry.assertThatWithRetry;
import static io.helidon.microprofile.metrics.HelloWorldResource.MESSAGE_TIMER;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * Class HelloWorldTest.
 */
@HelidonTest
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")
@AddConfigBlock(value = """
        metrics.auto-http-metrics.paths.0.path=/
        metrics.auto-http-metrics.paths.0.methods=HEAD,OPTIONS
        metrics.auto-http-metrics.paths.1.path=/withArg/{name}
        metrics.auto-http-metrics.paths.1.enabled=false
        """)

@AddBean(CatchAllExceptionMapper.class)
class HelloWorldPathMethodSelectionTest {

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
    void testSelectivity() throws NoSuchMethodException {
        // With the auto config present we need to ping the endpoints before looking for the metrics.

        // This should NOT create a timer for this endpoint.
        var helloResponse = webTarget.path("/helloworld").request(MediaType.TEXT_PLAIN_TYPE).get();
        assertThat("Hello World response", helloResponse.getStatus(), is(200));

        // This should create a timer.
        var headResponse = webTarget.path("/helloworld").request(MediaType.TEXT_PLAIN_TYPE).head();
        assertThat("HEAD response", headResponse.getStatus(), is(200));
        assertThat("HEAD header", headResponse.getHeaders(), hasEntry("X-result", List.of("good")));

        // This should NOT create a timer.
        var withNameResponse = webTarget.path("/helloworld/withArg/Joe").request(MediaType.TEXT_PLAIN_TYPE).get();
        assertThat("With name response", withNameResponse.getStatus(), is(200));

        // Wait for the metrics to update.
        pause();

        Map<MetricID, Timer> timers = restRequestMetricsRegistry.getTimers((id, metric) -> id.getName().equals("REST.request"));
        assertThat("REST request timers", timers.keySet(), hasSize(1));

        assertThat("Normal greeting timer",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("message")),
                   nullValue());

        assertThat("HEAD timer",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("plainHead", Request.class)),
                   notNullValue());

        assertThat("With name argument",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("messageWithArg", String.class)),
                   nullValue());

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
            return getSyntheticTimer(MetricsCdiExtension.restEndpointTimerMetricID(HelloWorldResource.class, method));
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
