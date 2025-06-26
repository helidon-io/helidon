/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

import java.util.Set;
import java.util.SortedMap;

import io.helidon.common.testing.junit5.MatcherWithRetry;
import io.helidon.microprofile.testing.AddBean;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.metrics.MetricID;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.Tag;
import org.eclipse.microprofile.metrics.Timer;
import org.eclipse.microprofile.metrics.annotation.RegistryScope;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@HelidonTest
@AddBean(TestRestServiceInheritance.BaseService.class)
@AddBean(TestRestServiceInheritance.ConcreteService.class)
@AddBean(TestRestServiceInheritance.App.class)
@AddConfig(key = "metrics." + MetricsCdiExtension.REST_ENDPOINTS_METRIC_ENABLED_PROPERTY_NAME, value = "true")

public class TestRestServiceInheritance {

    @Inject
    private WebTarget webTarget;

    @Inject
    @RegistryScope(scope = "base")
    private MetricRegistry metricRegistry;

    @ApplicationScoped
    @ApplicationPath("/testRestInh")
    public static class App extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return Set.of(ConcreteService.class);
        }

    }

    public static abstract class BaseService {

        @GET
        @Path("/base")
        @Produces(MediaType.TEXT_PLAIN)
        public String getFromBase() {
            return "base";
        }
    }

    @RequestScoped
    @Path("/testRestResource")
    public static class ConcreteService extends BaseService {

        @GET
        @Path("/concrete")
        @Produces(MediaType.TEXT_PLAIN)
        public String getFromConcrete() {
            return "concrete";
        }
    }

    @Test
    void ensureRestRequestMetricsIncludedInheritedMethod() {
        // Access the resources at the base and the concrete level to make sure they work so the REST request metrics
        // should truly be present and to make sure the metrics are updated correctly.
        webTarget.path("testRestInh/testRestResource/concrete").request(MediaType.TEXT_PLAIN).get(String.class);
        webTarget.path("testRestInh/testRestResource/base").request(MediaType.TEXT_PLAIN).get(String.class);

        Timer getFromBaseTimer = metricRegistry.getTimer(
                new MetricID("REST.request",
                             new Tag("class",
                                     "io.helidon.microprofile.metrics.TestRestServiceInheritance$ConcreteService"),
                             new Tag("method", "getFromBase")));
        assertThat("Timer from base", getFromBaseTimer, notNullValue());

        /*
        The Helidon code which updates the REST request metrics runs in a filter after chain.proceed()...therefore, after the
        response has been sent. That means it is possible, given thread scheduling, for this test code to receive the response
        and check the metric in the registry before the filter has updated the metric. So retry a reasonable amount.
         */
        MatcherWithRetry.assertThatWithRetry("Timer from base: count",
                                             getFromBaseTimer::getCount,
                                             is(1L),
                                             20,
                                             100);

        Timer getFromConcreteTimer = metricRegistry.getTimer(
                new MetricID("REST.request",
                             new Tag("class",
                                     "io.helidon.microprofile.metrics.TestRestServiceInheritance$ConcreteService"),
                             new Tag("method", "getFromConcrete")));
        assertThat("Timer from concrete subclass", getFromConcreteTimer, notNullValue());
        MatcherWithRetry.assertThatWithRetry("Timer from base: count",
                                             getFromConcreteTimer::getCount,
                                             is(1L),
                                             20,
                                             100);
        assertThat("Timer from concrete subclass: count", getFromConcreteTimer.getCount(), is(1L));

    }
}
