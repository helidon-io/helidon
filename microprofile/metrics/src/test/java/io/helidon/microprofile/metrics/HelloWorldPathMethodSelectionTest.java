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
import java.util.concurrent.TimeUnit;

import io.helidon.common.testing.junit5.MatcherWithRetry;
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
import org.hamcrest.FeatureMatcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
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

    @AfterAll
    public static void cleanup() {
        MetricsMpServiceTest.wrapupTest();
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

        // These should create timers.
        var headResponse = webTarget.path("/helloworld").request(MediaType.TEXT_PLAIN_TYPE).head();
        assertThat("HEAD response", headResponse.getStatus(), is(200));
        assertThat("HEAD header", headResponse.getHeaders(), hasEntry("X-result", List.of("good")));

        // This should NOT create a timer.
        var withNameResponse = webTarget.path("/helloworld/withArg/Joe").request(MediaType.TEXT_PLAIN_TYPE).get();
        assertThat("With name response", withNameResponse.getStatus(), is(200));

        // Wait for the metrics to update.
        Map<MetricID, Timer> timers =
                MatcherWithRetry.assertThatWithRetry("REST request timers",
                                                     () -> restRequestMetricsRegistry.getTimers((id, metric) ->
                                                                           id.getName().equals("REST.request")),
                                                     aMapWithSize(is(2)));

        assertThat("Normal greeting timer",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("message")),
                   notNullValue());

        assertThat("HEAD timer",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("plainHead", Request.class)),
                   notNullValue());

        assertThat("With name argument",
                   getSyntheticTimer(HelloWorldResource.class.getMethod("messageWithArg", String.class)),
                   nullValue());

    }

    private static org.hamcrest.Matcher<Map<?, ?>> aMapWithSize(org.hamcrest.Matcher<Integer> matcher) {
        return new FeatureMatcher<Map<?, ?>, Integer>(matcher, "has size", "matcher") {
            @Override
            protected Integer featureValueOf(Map<?, ?> actual) {
                return actual.size();
            }
        };
    }

    Timer getSyntheticTimer(Method method) {
            return getSyntheticTimer(MetricsCdiExtension.restEndpointTimerMetricID(HelloWorldResource.class, method));
    }

    Timer getSyntheticTimer(MetricID metricID) {

        Map<MetricID, Timer> timers = restRequestMetricsRegistry.getTimers();
        return timers.get(metricID);
    }
}
