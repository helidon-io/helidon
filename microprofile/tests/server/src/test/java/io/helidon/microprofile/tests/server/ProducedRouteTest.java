/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.tests.server;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.RoutingName;
import io.helidon.microprofile.server.RoutingPath;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddConfig;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webserver.http.HttpService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddBean(ProducedRouteTest.TestBean.class)
//method config
@AddConfig(key = ProducedRouteTest.TEST_BEAN_FQDN + ".coolerTestService." + RoutingPath.CONFIG_KEY_PATH,
        value = ProducedRouteTest.FILTERED_PATH)
//field config
@AddConfig(key = ProducedRouteTest.TEST_BEAN_FQDN + ".coolestFieldProducedService." + RoutingPath.CONFIG_KEY_PATH,
        value = "/")
@AddConfig(key = ProducedRouteTest.TEST_BEAN_FQDN + ".coolestFieldProducedService." + RoutingName.CONFIG_KEY_NAME,
        value = RoutingName.DEFAULT_NAME)
public class ProducedRouteTest {

    static final String TEST_BEAN_FQDN = "io.helidon.microprofile.tests.server.ProducedRouteTest$TestBean";
    static final String FILTERED_PATH = "/filtered";
    static final String UNFILTERED_PATH = "/unfiltered";

    static final String COOL_HEADER = "Cool-Header";
    static final HeaderName COOL_HEADER_NAME = HeaderNames.create(COOL_HEADER);
    static final String COOL_VALUE = "cool value";
    static final String COOLER_HEADER = "Cooler-Header";
    static final HeaderName COOLER_HEADER_NAME = HeaderNames.create(COOLER_HEADER);
    static final String COOLER_VALUE = "cooler value";
    static final String COOLEST_HEADER = "Coolest-Header";
    static final HeaderName COOLEST_HEADER_NAME = HeaderNames.create(COOLEST_HEADER);
    static final String COOLEST_VALUE = "coolest value";

    @Test
    void producedServiceGet(WebTarget target) throws ExecutionException, InterruptedException, TimeoutException {
        MultivaluedMap<String, Object> headers = target.path(FILTERED_PATH)
                .request()
                .async()
                .get()
                .get(2, TimeUnit.SECONDS)
                .getHeaders();

        assertThat(headers.getFirst(COOL_HEADER), is(COOL_VALUE));
        assertThat(headers.getFirst(COOLER_HEADER), is(COOLER_VALUE));
        assertThat(headers.getFirst(COOLEST_HEADER), is(COOLEST_VALUE));
    }

    @Test
    void producedServicePut(WebTarget target) throws ExecutionException, InterruptedException, TimeoutException {
        MultivaluedMap<String, Object> headers = target.path(FILTERED_PATH)
                .request()
                .async()
                .put(Entity.text(""))
                .get(2, TimeUnit.SECONDS)
                .getHeaders();

        assertThat(headers.getFirst(COOL_HEADER), is(COOL_VALUE));
        assertThat(headers.getFirst(COOLER_HEADER), is(COOLER_VALUE));
        assertThat(headers.getFirst(COOLEST_HEADER), is(COOLEST_VALUE));
    }

    @Test
    void configuredProducedService(WebTarget target) throws ExecutionException, InterruptedException, TimeoutException {
        MultivaluedMap<String, Object> headers = target.path(UNFILTERED_PATH)
                .request()
                .async()
                .get()
                .get(2, TimeUnit.SECONDS)
                .getHeaders();

        assertThat(headers.getFirst(COOL_HEADER), is(COOL_VALUE));
        assertThat(headers.getFirst(COOLER_HEADER), is(equalTo(null)));
        assertThat(headers.getFirst(COOLEST_HEADER), is(COOLEST_VALUE));
    }

    @ApplicationScoped
    @Path("/")
    public static class TestBean {

        @PUT
        @Path(FILTERED_PATH)
        public Response filteredPut(String data) {
            return Response.ok().build();
        }

        @GET
        @Path(FILTERED_PATH)
        public Response filteredGet() {
            return Response.ok().build();
        }

        @GET
        @Path(UNFILTERED_PATH)
        public Response unfilteredGet() {
            return Response.ok().build();
        }

        @Produces
        @ApplicationScoped
        @RoutingName(value = "wrong", required = true)
        @RoutingPath("wrong")
        HttpService coolestFieldProducedService = rules -> rules.any((req, res) -> {
            res.headers().set(COOLEST_HEADER_NAME, COOLEST_VALUE);
            res.next();
        });


        @Produces
        @ApplicationScoped
        @RoutingName(RoutingName.DEFAULT_NAME)
        public HttpService coolTestService() {
            return rules -> rules.any((req, res) -> {
                res.headers().set(COOL_HEADER_NAME, COOL_VALUE);
                res.next();
            });
        }

        @Produces
        @ApplicationScoped
        public HttpService coolerTestService() {
            return rules -> rules.any((req, res) -> {
                res.headers().set(COOLER_HEADER_NAME, COOLER_VALUE);
                res.next();
            });
        }

    }
}
