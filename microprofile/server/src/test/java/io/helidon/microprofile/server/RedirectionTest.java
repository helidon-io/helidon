/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.microprofile.server;

import java.net.URI;

import io.helidon.microprofile.tests.junit5.AddBean;
import io.helidon.microprofile.tests.junit5.AddExtension;
import io.helidon.microprofile.tests.junit5.DisableDiscovery;
import io.helidon.microprofile.tests.junit5.HelidonTest;

import jakarta.enterprise.inject.spi.CDI;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@DisableDiscovery
@AddBean(RedirectionTest.TestResource.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
class RedirectionTest {
    @Test
    void streamingOutput() {
        Client client = ClientBuilder.newClient();

        int port = CDI.current().getBeanManager().getExtension(ServerCdiExtension.class).port();
        WebTarget baseTarget = client.target("http://[::1]:" + port);

        Response response = baseTarget.path("/uri")
                .request()
                .get();

        assertThat(response.readEntity(String.class), is("http://[0:0:0:0:0:0:0:1]:" + port + "/uri"));

        response = baseTarget.path("/redirect")
                .request()
                .get();

        assertThat(response.getStatus(), is(Response.Status.OK.getStatusCode()));
        assertThat(response.readEntity(String.class), is("http://[0:0:0:0:0:0:0:1]:" + port + "/uri"));
    }

    @Path("/")
    public static class TestResource {

        @GET
        @Path("/uri")
        public String uri(@Context UriInfo uriInfo) {
            return uriInfo.getRequestUri().toString();
        }

        @GET
        @Path("/redirect")
        public Response redirect() {
            return Response
                    .seeOther(URI.create("/uri"))
                    .build();
        }
    }
}