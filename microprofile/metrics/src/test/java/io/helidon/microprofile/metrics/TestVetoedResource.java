/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

import javax.enterprise.inject.se.SeContainer;
import javax.enterprise.inject.se.SeContainerInitializer;
import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.Server;
import io.helidon.microprofile.server.ServerCdiExtension;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.metrics.MetricID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Properties;

/**
 * Makes sure that the vetoed resources' metrics are not registered.
 *
 * For these tests we explicitly enable the REST.request synthetic metrics so we can make sure one is not created for an
 * endpoint method on a vetoed bean.
 */
public class TestVetoedResource extends MetricsMpServiceTest {

    private static SeContainer seContainer;

    @BeforeAll
    public static void init() throws Exception {
        /*
           We need to add the test CDI extension, which means we'll need to initialize the SE CDI container, which in turn
           means that CDI will automatially start the web server, rather than using the typical Server.builder() sequence.
         */
        Properties configProperties = new Properties();
        configProperties.setProperty("mp.initializer.allow", "true");
        Config config = MetricsMpServiceTest.initializeConfig(configProperties);
        seContainer = SeContainerInitializer.newInstance()
                .addExtensions(VetoCdiExtension.class)
                .initialize();
        MetricsMpServiceTest.initializeServer(config);
    }

    @Test
    void testVetoedResourceDoesNotRespond() throws NoSuchMethodException {
        // This really checks web server behavior, not metrics behavior per se.
        Response res = client.target(baseUri())
                .path("/vetoed")
                .request()
                .get();
        // JAX-RS should not handle an endpoint on a vetoed bean.
        assertThat(res.getStatus(), is(Response.Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    void testNoAnnotatedMetricForVetoedResource() throws NoSuchMethodException {
        // The metrics CDI extension should ignore the vetoed resource's explicitly-annotated metrics.
        MetricID vetoedID = new MetricID(VetoedResource.COUNTER_NAME);
        assertThat("Metrics CDI extension incorrectly registered a metric on a vetoed resource",
                registry.getCounters()
                        .containsKey(vetoedID), is(false));
    }

    @Test
    void testNoSyntheticSimplyTimedMetricForVetoedResource() throws NoSuchMethodException {
        // Makes sure that a vetoed JAX-RS resource with an explicit metric annotation was not registered with a synthetic
        // SimplyTimed metric.
        Method method = VetoedResource.class.getMethod("get");
        assertThat(
            "Metrics CDI extension incorrectly registered a synthetic simple timer on a vetoed resource JAX-RS endpoint "
                    + "method with an explicit metrics annotation",
            MetricsCdiExtension.getRegistryForSyntheticSimpleTimers()
                    .getSimpleTimers()
                    .containsKey(MetricsCdiExtension.syntheticSimpleTimerMetricID(method)),
                is(false));
    }

    @Test
    void testNoSyntheticSimplyTimedMetricForVetoedResourceWithJaxRsEndpointButOtherwiseUnmeasured() throws NoSuchMethodException {
        // Makes sure that a vetoed JAX-RS resource with no explicit metric annotation was not registered with a synthetic
        // SimpleTimed metric.
        Method method = VetoedJaxRsButOtherwiseUnmeasuredResource.class.getMethod("get");
        assertThat(
                "Metrics CDI extension incorrectly registered a synthetic simple timer on JAX-RS endpoint method with no "
                        + "explicit metrics annotation: "
                    + VetoedJaxRsButOtherwiseUnmeasuredResource.class.getName() + "#" + method.getName(),
                MetricsCdiExtension.getRegistryForSyntheticSimpleTimers()
                        .getSimpleTimers()
                        .containsKey(MetricsCdiExtension.syntheticSimpleTimerMetricID(method)),
                is(false));
    }
}
