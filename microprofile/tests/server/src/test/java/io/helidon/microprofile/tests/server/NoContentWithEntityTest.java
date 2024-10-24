/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import io.helidon.http.Status;
import io.helidon.microprofile.server.JaxRsCdiExtension;
import io.helidon.microprofile.server.ServerCdiExtension;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.AddExtension;
import io.helidon.microprofile.testing.junit5.DisableDiscovery;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.ext.cdi1x.internal.CdiComponentProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@DisableDiscovery
@AddBean(NoContentWithEntityTest.TestResource.class)
@AddExtension(ServerCdiExtension.class)
@AddExtension(JaxRsCdiExtension.class)
@AddExtension(CdiComponentProvider.class)
@Disabled
class NoContentWithEntityTest {
    @Inject
    WebTarget target;

    @Test
    void streamingOutput() {
        Response response = target.path("/noContent")
                .request()
                .get();

        assertThat(response.getStatus(), is(Status.INTERNAL_SERVER_ERROR_500.code()));

        response = target.path("/ok")
                .request()
                .get();
        assertThat(response.getStatus(), is(Status.OK_200.code()));
        assertThat(response.readEntity(String.class), is("hello"));
    }

    @Path("/")
    public static class TestResource {

        @GET
        @Path("/noContent")
        public Response noContent() {
            return Response.noContent()
                    .entity("hello")        // should be rejected by Jersey
                    .build();
        }

        @GET
        @Path("/ok")
        public Response ok() {
            return Response
                    .ok("hello")
                    .build();
        }
    }
}