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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;
import java.util.stream.IntStream;

import javax.inject.Inject;
import javax.json.JsonObject;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.helidon.config.testing.OptionalMatcher;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.Metadata;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.SimpleTimer;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.annotation.Metric;
import org.eclipse.microprofile.metrics.annotation.RegistryType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import static io.helidon.microprofile.metrics.HelloWorldResource.MESSAGE_SIMPLE_TIMER;
import static io.helidon.microprofile.metrics.HelloWorldResource.PARAMETER_INJECTED_METRIC_NAME;
import static io.helidon.microprofile.metrics.HelloWorldResource.PARAMETER_INJECTED_METRIC_NAME_2;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;

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
    MetricRegistry syntheticSimpleTimerRegistry;

    @BeforeAll
    public static void initialize() {
        System.setProperty("jersey.config.server.logging.verbosity", "FINE");
    }

    @AfterAll
    public static void cleanup() {
        MetricsMpServiceTest.wrapupTest();
    }

    @BeforeEach
    public void registerCounter() {
        MetricsMpServiceTest.registerCounter(registry, "helloCounter");
    }

    @Test
    @Order(1)
    void checkExplicitlyNamedAbsoluteClassInjection() {
        checkInjectedRegistration(HelloWorldResource.CLASS_INJECTED_METRIC_NAME,
                                  null,
                                  "explicitly-named absolute class-injected");
    }

    @Test
    @Order(1)
    void checkAutomaticallylNamedAbsoluteClassInjection() throws NoSuchFieldException {
        checkInjectedRegistration("autoNamedAbsoluteClassInjectedCounter",
                                  null,
                                  "automatically-named absolute class-injected");
    }

    @Test
    @Order(1)
    void checkAutomaticallyNamedRelativeClassInjection() {
        checkInjectedRegistration(HelloWorldResource.class.getName() + ".autoNamedRelativeClassInjectedCounter",
                                          null,
                                  "automatically-named relative class-injected");
    }

    @Test
    @Order(1)
    void checkExplicitlyNamedAbsoluteParameterInjection() {
        checkInjectedRegistration(PARAMETER_INJECTED_METRIC_NAME,
                                  null,
                                  "explicitly-named absolute parameter-injected");
    }

    @Test
    @Order(1)
    void checkAutomaticallyNamedRelativeParameterInjection() {
        checkInjectedRegistration(HelloWorldResource.class.getName()
                                      + "."
                                      + PARAMETER_INJECTED_METRIC_NAME_2,
                                  new Tag[] {new Tag("t1", "v1"), new Tag("t2", "v2")},
                                  "automatically-named relative parameter-injected");
    }

    @Test
    @Order(1) // Run before all others so no resource classes are realized (which can trigger metrics registration from producers)
    void checkInjectedMetricRegistration() {

        checkInjectedRegistration(HelloWorldResource.CLASS_INJECTED_METRIC_NAME,
                                  null,
                                  "explicitly-named absolute class-injected");
    }

    @Test
    @Order(1)
    void checkInjectedMetricRegistrationWithoutMetricAnno() {
        checkInjectedRegistration(HelloWorldResource.class.getName() + ".bareCounter",
                                  null,
                                  null);
    }

    private Counter checkInjectedRegistration(String metricName,
                                           Tag[] tags,
                                           String expectedDesc) {
        return checkInjectedRegistration(metricName, tags, expectedDesc, 0);
    }

    private Counter checkInjectedRegistration(String metricName,
                                              Tag[] tags,
                                              String expectedDesc,
                                              long expectedValue) {

        if (tags == null) {
            tags = new Tag[0];
        }

        Metadata metadataForClassInjectedExplicitlyNamedCounter = registry.getMetadata()
                .get(metricName);
        assertThat("Metadata for " + metricName,
                   metadataForClassInjectedExplicitlyNamedCounter, is(notNullValue()));
        if (expectedDesc != null) {
            assertThat("Description for " + metricName,
                       metadataForClassInjectedExplicitlyNamedCounter.getDescription(),
                       OptionalMatcher.value(containsString(expectedDesc)));
        } else {
            assertThat("Description for " + metricName,
                       metadataForClassInjectedExplicitlyNamedCounter.getDescription(),
                       OptionalMatcher.empty());
        }
        // Look for the counter based on only the name. Save matching metric IDs to make sure
        // they (it) match the expected tags.

        Map<MetricID, Counter> sameNamedCounters = registry.getCounters((id, metric) ->
                                                       id.getName().equals(metricName));
        // We expect only one match because of the way we set up the test annotations, and
        // its tags should match what we expect.
        assertThat("Number of counters matching " + metricName,
                   sameNamedCounters.size(), is(1));
        Map.Entry<MetricID, Counter> matchedEntry = sameNamedCounters.entrySet().iterator().next();
        assertThat("Tags for " + metricName,
                   matchedEntry.getKey().getTagsAsArray(),
                   is(tags));
        assertThat("Value for " + metricName,
                   matchedEntry.getValue().getCount(),
                   is(expectedValue));
        return matchedEntry.getValue();
    }

    @Test
    public void testMetrics() {
        final int iterations = 1;
        IntStream.range(0, iterations).forEach(
                i -> webTarget
                        .path("helloworld")
                        .request()
                        .accept(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));
        assertThat("Value of explicitly-updated counter", registry.counter("helloCounter").getCount(),
                is((long) iterations));

        Counter classLevelCounterForConstructor =
                registry.getCounters().get(new MetricID(HelloWorldResource.class.getName() + "." + HelloWorldResource.class.getSimpleName()));
        assertThat("Class-declared counter metric for constructor", classLevelCounterForConstructor, is(notNullValue()));
        assertThat("Value of interceptor-updated class-level counter for constructor", classLevelCounterForConstructor.getCount(),
                is((long) iterations));

        Counter classLevelCounterForMethod =
                registry.getCounters().get(new MetricID(HelloWorldResource.class.getName() + ".message"));
        assertThat("Class-declared counter metric for constructor", classLevelCounterForMethod, is(notNullValue()));
        assertThat("Value of interceptor-updated class-level counter for method", classLevelCounterForMethod.getCount(),
                is((long) iterations));

        SimpleTimer simpleTimer = getSyntheticSimpleTimer("message");
        assertThat("Synthetic simple timer", simpleTimer, is(notNullValue()));
        assertThat("Synthetic simple timer count value", simpleTimer.getCount(), is((long) iterations));

        checkMetricsUrl(iterations);
    }

    @Test
    public void testSyntheticSimpleTimer() {
        testSyntheticSimpleTimer(1L);
    }

    void testSyntheticSimpleTimer(long expectedSyntheticSimpleTimerCount) {
        IntStream.range(0, (int) expectedSyntheticSimpleTimerCount).forEach(
                i -> webTarget
                        .path("helloworld/withArg/Joe")
                        .request(MediaType.TEXT_PLAIN_TYPE)
                        .get(String.class));

        SimpleTimer explicitSimpleTimer = registry.simpleTimer(MESSAGE_SIMPLE_TIMER);
        assertThat("SimpleTimer from explicit @SimplyTimed", explicitSimpleTimer, is(notNullValue()));
        assertThat("SimpleTimer from explicit @SimpleTimed count", explicitSimpleTimer.getCount(),
                is(expectedSyntheticSimpleTimerCount));

        SimpleTimer syntheticSimpleTimer = getSyntheticSimpleTimer("messageWithArg", String.class);
        assertThat("SimpleTimer from @SyntheticSimplyTimed", syntheticSimpleTimer, is(notNullValue()));
        assertThat("SimpleTimer from @SyntheticSimplyTimed count", syntheticSimpleTimer.getCount(), is(expectedSyntheticSimpleTimerCount));
    }

    SimpleTimer getSyntheticSimpleTimer(String methodName, Class<?>... paramTypes) {
        try {
            return getSyntheticSimpleTimer(HelloWorldResource.class.getMethod(methodName, paramTypes));
        } catch (NoSuchMethodException ex) {
            throw new RuntimeException(ex);
        }
    }

    SimpleTimer getSyntheticSimpleTimer(Method method) {
            return getSyntheticSimpleTimer(MetricsCdiExtension.syntheticSimpleTimerMetricID(method));
    }

    SimpleTimer getSyntheticSimpleTimer(MetricID metricID) {

        Map<MetricID, SimpleTimer> simpleTimers = syntheticSimpleTimerRegistry.getSimpleTimers();
        return simpleTimers.get(metricID);
    }

    void checkMetricsUrl(int iterations) {
        JsonObject app = webTarget
                .path("metrics")
                .request()
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(JsonObject.class)
                .getJsonObject("application");
        assertThat(app.getJsonNumber("helloCounter").intValue(), is(iterations));
    }
}
