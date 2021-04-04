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

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class AsyncResourceTest extends Application {
    private static Client client;

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void destroyClass() {
        client.close();
    }

    @Test
    void asyncResource() {
        Server server = Server.builder()
                .addApplication("/async-app", new TestApp())
                .build();

        server.start();

        try {
            WebTarget target = client.target("http://localhost:" + server.port());

            String getResponse = target.path("/async-app/asyncGet").request().get(String.class);
            assertThat(getResponse, is(equalTo("testResponseGet")));

            String putResponse = target.path("/async-app/asyncPut").request().put(Entity.text(""), String.class);
            assertThat(putResponse, is(equalTo("testResponsePut")));
        } finally {
            server.stop();
        }
    }

    private final class TestApp extends Application {

        @Override
        public Set<Object> getSingletons() {
            return Set.of(new TestResource());
        }
    }

    @Path("/")
    public static final class TestResource {

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
