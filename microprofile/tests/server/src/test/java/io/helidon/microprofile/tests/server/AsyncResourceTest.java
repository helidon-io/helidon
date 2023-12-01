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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.helidon.microprofile.testing.junit5.AddBean;
import io.helidon.microprofile.testing.junit5.HelidonTest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

@HelidonTest
@AddBean(AsyncResourceTest.TestResource.class)
public class AsyncResourceTest {

    @Test
    void asyncResourceGet(WebTarget target) {
        String getResponse = target.path("/asyncGet").request().get(String.class);
        assertThat(getResponse, is(equalTo("testResponseGet")));
    }

    @Test
    void asyncResourcePut(WebTarget target) {
        String putResponse = target.path("/asyncPut").request().put(Entity.text(""), String.class);
        assertThat(putResponse, is(equalTo("testResponsePut")));
    }

    @Path("/")
    public static class TestResource {

        @GET
        @Path("asyncGet")
        @Produces(MediaType.TEXT_PLAIN)
        public CompletionStage<String> foo() {
            return CompletableFuture.completedStage("testResponseGet");
        }

        @PUT
        @Path("asyncPut")
        public CompletionStage<Response> bar() {
            return CompletableFuture.completedFuture(Response.ok().entity("testResponsePut").build());
        }
    }
}
