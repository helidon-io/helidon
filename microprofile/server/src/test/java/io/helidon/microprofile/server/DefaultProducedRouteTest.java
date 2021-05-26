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

package io.helidon.microprofile.server;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddConfig;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;
import io.helidon.webserver.Service;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

@HelidonTest
@DisableDiscovery
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@AddBean(DefaultProducedRouteTest.TestBean.class)
@AddConfig(key = DefaultProducedRouteTest.TEST_BEAN_FQDN + ".coolerTestService." + RoutingPath.CONFIG_KEY_PATH,
        value = DefaultProducedRouteTest.FILTERED_PATH)
public class DefaultProducedRouteTest {

    static final String TEST_BEAN_FQDN = "io.helidon.microprofile.server.DefaultProducedRouteTest$TestBean";
    static final String FILTERED_PATH = "/filtered";
    static final String UNFILTERED_PATH = "/unfiltered";

    static final String COOL_HEADER = "Cool-Header";
    static final String COOL_VALUE = "cool value";
    static final String COOLER_HEADER = "Cooler-Header";
    static final String COOLER_VALUE = "cooler value";

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
    }

    @ApplicationScoped
    @Path("/")
    public static class TestBean {

        @PUT
        @Path(FILTERED_PATH)
        public Response filteredPut(@HeaderParam(COOL_HEADER) String coolHeader, String data) {
            return Response.ok(coolHeader).build();
        }

        @GET
        @Path(FILTERED_PATH)
        public Response filteredGet(@HeaderParam(COOL_HEADER) String coolHeader) {
            return Response.ok(coolHeader).build();
        }

        @GET
        @Path(UNFILTERED_PATH)
        public Response unfilteredGet(@HeaderParam(COOL_HEADER) String coolHeader) {
            return Response.ok(coolHeader).build();
        }

        @Produces
        @ApplicationScoped
        @RoutingName(RoutingName.DEFAULT_NAME)
        public Service coolTestService() {
            return rules -> rules.any((req, res) -> {
                res.headers().put(COOL_HEADER, COOL_VALUE);
                req.next();
            });
        }

        @Produces
        @ApplicationScoped
        public Service coolerTestService() {
            return rules -> rules.any((req, res) -> {
                res.headers().put(COOLER_HEADER, COOLER_VALUE);
                req.next();
            });
        }

    }
}
