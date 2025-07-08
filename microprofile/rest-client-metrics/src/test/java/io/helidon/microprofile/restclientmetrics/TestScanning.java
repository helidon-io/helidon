/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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
package io.helidon.microprofile.restclientmetrics;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.Counted;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@HelidonTest
@AddBean(TestScanning.ServiceClient.class)
@AddBean(TestScanning.ServiceClientParent.class)
@AddBean(TestScanning.ExplicitlyRegisteredServiceClientWithOnlyInheritedRestMethods.class)
@AddBean(TestScanning.ServiceClientWithPathAndWithOnlyInheritedRestMethods.class)
@AddBean(TestService.class)
class TestScanning {

    @Inject
    WebTarget webTarget;

    private ServiceClient serviceClient;

    @BeforeEach
    void init() {
        serviceClient = RestClientBuilder.newBuilder()
                .baseUri(webTarget.getUri())
                .build(ServiceClient.class);
        RestClientBuilder.newBuilder()
                .baseUri(webTarget.getUri())
                .build(ExplicitlyRegisteredServiceClientWithOnlyInheritedRestMethods.class);
        RestClientBuilder.newBuilder()
                .baseUri(webTarget.getUri())
                .build(ServiceClientWithPathAndWithOnlyInheritedRestMethods.class);

    }

    @Test
    void annotationsOnMethods() throws NoSuchMethodException {
        RestClientMetricsCdiExtension extension = CDI.current().getBeanManager()
                .getExtension(RestClientMetricsCdiExtension.class);
        Map<Method, List<RestClientMetricsCdiExtension.MetricsUpdateWork>> filterWorkByMethod =
                extension.metricsUpdateWorkByMethod();
        assertThat("Check for expected filter work", filterWorkByMethod.keySet(),
                   allOf(hasItems(equalTo(ServiceClient.class.getMethod("get")),
                                  equalTo(ServiceClient.class.getMethod("put"))),
                         not(hasItems(equalTo(ServiceClient.class.getMethod("timedNonRestMethod")),
                                      equalTo(ServiceClient.class.getMethod("countedNonRestMethod")),
                                      equalTo(ServiceClient.class.getMethod("unannotatedRestMethod"))))));
    }

    @Test
    void checkMetricsRegistrationsFromMethods() {
        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class).get();

        // get method

        // relative, automatic name = declaring-class.element-name
        Timer getTimer = metricRegistry.getTimer(new MetricID(ServiceClient.class.getCanonicalName() + ".get"));
        assertThat("Relative automatically-named timer for get", getTimer, notNullValue());

        // absolute, explicit name = specified-name
        Counter getCounter = metricRegistry.getCounter(new MetricID("getAbs"));
        assertThat("Absolute explicitly-named counter for get", getCounter, notNullValue());

        // get2 method

        // absolute, automatic name = element-name
        Timer get2Timer = metricRegistry.getTimer(new MetricID("get2"));
        assertThat("Absolute automatically-named timer for get2", get2Timer, notNullValue());

        // relative, explicit name = declaring-class.specified-name
        Counter get2Counter = metricRegistry.getCounter(new MetricID(ServiceClient.class.getCanonicalName() + ".relget2"));
        assertThat("Relative explicitly-named counter for get2", get2Counter, notNullValue());

        // timedNonRestMethod
        Timer timedNonRestMethodTimer = metricRegistry.getTimer(new MetricID(ServiceClient.class.getCanonicalName() +
                                                                                     ".timedNonRestMethod"));
        assertThat("Relative automatically-named timer for non-REST method", timedNonRestMethodTimer, nullValue());

        // put method

        // relative, automatic name = declaring-class.element-name
        Counter putCounter = metricRegistry.getCounter(new MetricID(ServiceClient.class.getCanonicalName() + ".put"));
        assertThat("Relative automatically-named counter for put", putCounter, notNullValue());

        // absolute, explicit name = specified-name
        Timer putTimer = metricRegistry.getTimer(new MetricID("putAbs"));
        assertThat("Absolute explicitly-named timer for put", putTimer, notNullValue());

        // countedNonRestMethod
        Counter nonRestMethodCounter = metricRegistry.getCounter(new MetricID("shouldNotAppear"));
        assertThat("Counter for non-REST method", nonRestMethodCounter, nullValue());

        // non-REST and unmeasured
        var metrics = metricRegistry.getMetrics();
        assertThat("Metrics that should not appear",
                   metrics.keySet(),
                   allOf(not(contains("shouldNotAppear")),
                         not(contains("countedNonRestMethod"))));
    }

    @Test
    void checkMetricsRegistrationsFromType() {
        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class).get();

        // get method

        // type-level absolute with explicit name = specified-name.method-name
        Timer getTimerFromType = metricRegistry.getTimer(new MetricID("typeLevelAbs.get"));
        assertThat("Type-level timer with absolute explicit name", getTimerFromType, notNullValue());

        // type-level relative with explicit name = package.specified-name.method-name
        Counter getCounterFromType = metricRegistry.getCounter(new MetricID(ServiceClient.class.getPackageName()
                                                                                    + ".typeLevelRel.get"));
        assertThat("Type-level counter with relative explicit name", getCounterFromType, notNullValue());

        Counter unmeasuredGetCounterFromType = metricRegistry.getCounter(new MetricID(ServiceClient.class.getPackageName()
                                                                                              + ".typeLevelRel"
                                                                                              + ".unannotatedRestMethod"));
        assertThat("unmeasuredRest counter from type-level annotation", unmeasuredGetCounterFromType, notNullValue());
    }

    @Test
    void checkMetricsUpdates() {

        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class).get();

        List<TimerInfo> timers = new ArrayList<>();

        // All on the get method.

        // relative automatically-named timer on the get method in the subinterface = subtype.method-name
        TimerInfo getTimerInfo = TimerInfo.create(metricRegistry, ServiceClient.class.getCanonicalName() + ".get");
        assertThat("Relative automatically-named subinterface method-level timer for get method", getTimerInfo, notNullValue());
        Duration elapsedTimeBefore = getTimerInfo.timer.getElapsedTime();

        timers.add(getTimerInfo);

        // absolute explicitly-named timer on the subinterface = specified-value.method-name

        TimerInfo getTimerFromTypeInfo = TimerInfo.create(metricRegistry, "typeLevelAbs.get");

        assertThat("Absolute explicitly-named timer from the subinterface", getTimerFromTypeInfo.timer, notNullValue());

        timers.add(getTimerFromTypeInfo);

        // relative automatically-named timer on parentGet method on superinterface = subtype.method-name
        TimerInfo getTimerFromSuperTypeInfo = TimerInfo.create(metricRegistry, ServiceClient.class.getCanonicalName()
                + ".get");
                assertThat("Relative automatically-named timer on method in superinterface", getTimerFromSuperTypeInfo.timer,
                notNullValue());

        timers.add(getTimerFromSuperTypeInfo);

        // relatively explicitly-named timer on superinterface = subtype-package.specified-name.method-name
        TimerInfo inheritedGetMethodTimerInfo = TimerInfo.create(metricRegistry, ServiceClient.class.getPackageName()
                + ".parentLevelRel.get");
        assertThat("Inherited relative auto-named type-level timer for get method",
                   inheritedGetMethodTimerInfo.timer,
                   notNullValue());

        timers.add(inheritedGetMethodTimerInfo);

        String timedGetResult = serviceClient.get();

        assertThat("Timed get result", timedGetResult, equalTo("get"));
        Duration elapsedTimeAfter = getTimerInfo.timer.getElapsedTime();
        assertThat("Timer delta", elapsedTimeAfter.compareTo(elapsedTimeBefore), greaterThan(0));

        String parentGetResult = serviceClient.parentGet();
        assertThat("Parent get result", parentGetResult, equalTo("parent get"));

        for (TimerInfo timerInfo : timers) {
            assertThat("Timer for timer info " + timerInfo.metricId, timerInfo.timer, notNullValue());
            assertThat("Counter update for " + timerInfo.metricId,
                       timerInfo.timer.getCount(),
                       greaterThan(timerInfo.beforeCount));
        }
    }

    @Test
    void checkInheritance() {
        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class).get();

        // parentGet method

        // method-level relative automatic name = declaring-class.method-name (declaring class is the superinterface)
        Timer parentGetMethodTimer = metricRegistry.getTimer(new MetricID(ServiceClientParent.class.getCanonicalName()
                                                                                  + ".parentGet"));
        assertThat("Relative automatically-named timer for inherited parentGet method", parentGetMethodTimer, notNullValue());

        // get method

        // method-level absolute explicit name = specified-name
        Counter getMethodCounterFromParent = metricRegistry.getCounter(new MetricID("parentGetAbs"));
        assertThat("Absolute explicitly-named counter for inherited get method", getMethodCounterFromParent, notNullValue());

        // type-level relative explicit name = package-of-declaring-class.specified-name.method-name
        Timer superTypeLevelTimerForGet = metricRegistry.getTimer(new MetricID(ServiceClientParent.class.getPackageName() +
                                                                                       ".parentLevelRel.get"));
        assertThat("Type-level relative explicitly-named counter for inherited get method",
                   superTypeLevelTimerForGet,
                   notNullValue());

        // put method

        // type-level absolute explicit name = specified-name.method-name
        Counter inheritedPutMethodCounter = metricRegistry.getCounter(new MetricID("parentLevelAbs.put"));
        assertThat("Type-level absolute explicitly-named counter inherited for put method",
                   inheritedPutMethodCounter,
                   notNullValue());
    }

    @Test
    void checkInheritanceOnlyRestClient() {
        var inheritanceOnlyRestClient = RestClientBuilder.newBuilder()
                .baseUri(webTarget.getUri())
                .build(ServiceClientWithOnlyInheritedRestClientAnnotations.class);

        MetricRegistry metricRegistry = CDI.current().select(MetricRegistry.class).get();

        // parentGet method
        Timer parentGetMethodTimer = metricRegistry.getTimer(new MetricID(JustInTimeParent.class.getCanonicalName()
                                                                          + ".parentGet"));
        assertThat("Relative automatically named timer for inherited parentGet method", parentGetMethodTimer, notNullValue());

    }

    @Timed(name = "parentLevelRel")
    @Counted(name = "parentLevelAbs", absolute = true)
    interface ServiceClientParent {

        @Timed
        @GET
        @Path("/parentGet")
        String parentGet();

        @Counted(name = "parentGetAbs", absolute = true)
        @GET
        @Path("/get")
        String get();

    }

    @Path(TestService.RESOURCE_PATH)
    @Counted(name = "typeLevelRel")
    @Timed(name = "typeLevelAbs", absolute = true)
    interface ServiceClient extends ServiceClientParent {

        @Timed
        @Counted(absolute = true, name = "getAbs")
        @GET
        @Path("/get")
        String get();

        @Timed(absolute = true)
        @Counted(name = "relget2")
        @GET
        @Path("/get2")
        String get2();

        @Timed
        void timedNonRestMethod();

        @Counted
        @Timed(absolute = true, name = "putAbs")
        @PUT
        @Path("/put")
        void put();

        @Counted(name = "shouldNotAppear", absolute = true)
        void countedNonRestMethod();

        @HEAD
        @Path("/unannotatedRestMethod")
        void unannotatedRestMethod();
    }

    // Make sure REST client metrics properly handles an explicitly-registered interface with only inherited REST methods.
    @RegisterRestClient
    interface ExplicitlyRegisteredServiceClientWithOnlyInheritedRestMethods extends ServiceClientParent {
    }

    // Make sure REST client metrics properly handles an interface with only @Path and only inherited REST methods.
    @Path("/restClientWithPathButNoneOfItsOwnRestMethods")
    interface ServiceClientWithPathAndWithOnlyInheritedRestMethods extends ServiceClientParent {
    }

    // Parent to interface with none of its own REST client-related annotations.
    @Counted(name = "typeLevelRel")
    @Timed(name = "typeLevelAbs", absolute = true)
    interface JustInTimeParent {

        @Timed
        @GET
        @Path("/parentGet")
        String parentGet();
    }

    // Interface with REST client-related annotations only through inheritance. Discovered only upon explicit use.
    interface ServiceClientWithOnlyInheritedRestClientAnnotations extends JustInTimeParent {
    }

    private record TimerInfo(Timer timer, MetricID metricId, long beforeCount) {

        static TimerInfo create(MetricRegistry metricRegistry, String metricName) {
            MetricID metricID = new MetricID(metricName);
            Timer timer = metricRegistry.getTimer(metricID);
            return new TimerInfo(timer, metricID, timer != null ? timer.getCount() : 0L);
        }
    }

}
