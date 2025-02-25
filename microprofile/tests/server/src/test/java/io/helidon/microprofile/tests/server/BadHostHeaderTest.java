/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates.
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

import io.helidon.http.Header;
import io.helidon.http.HeaderValues;
import io.helidon.http.Status;
import io.helidon.microprofile.testing.AddConfig;
import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.ServerRequest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@HelidonTest
@AddBean(BadHostHeaderTest.TestResource.class)
@AddConfig(key = "server.error-handling.include-entity", value = "true")
public class BadHostHeaderTest {
    private static final Header BAD_HOST_HEADER = HeaderValues.create("Host", "localhost:808a");

    @Test
    void testGetGoodHeader(WebTarget target) {
        String getResponse = target.path("/get").request().get(String.class);
        assertThat(getResponse, is("localhost"));
    }

    @Test
    void testGetBadHeader(WebTarget target) {
        WebClient webClient = WebClient.builder()
                .baseUri(target.getUri())
                .build();
        var response = webClient.get("/get")
                .header(BAD_HOST_HEADER)
                .request(String.class);
        assertThat(response.status(), is(Status.BAD_REQUEST_400));
        assertThat(response.entity(), is("Invalid port of the host header: 808a"));
    }

    @Path("/")
    public static class TestResource {
        @Context ServerRequest request;

        @GET
        @Path("get")
        @Produces(MediaType.TEXT_PLAIN)
        public String getIt() {
            return request.requestedUri().host();
        }
    }
}
