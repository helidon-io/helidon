/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.microprofile.cors;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

import java.util.Set;

import io.helidon.common.CollectionsHelper;
import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;
import static io.helidon.microprofile.cors.CrossOrigin.ACCESS_CONTROL_ALLOW_ORIGIN;

/**
 * Class CrossOriginTest.
 */
public class CrossOriginTest {

    private static Client client;

    @BeforeAll
    static void initClass() {
        client = ClientBuilder.newClient();
    }

    @AfterAll
    static void destroyClass() {
        client.close();
    }

    static public class CorsApplication extends Application {

        @Override
        public Set<Class<?>> getClasses() {
            return CollectionsHelper.setOf(CorsResource.class);
        }
    }

    @Path("/cors")
    static public class CorsResource {

        @GET
        @CrossOrigin
        public String cors1() {
            return "cors1";
        }
    }

    @Test
    void testCors() {
        Server server = Server.builder()
                .addApplication("/app", new CorsApplication())
                .build();
        server.start();

        try {
            WebTarget target = client.target("http://localhost:" + server.port());
            Response response = target.path("/app/cors").request().get();
            assertThat(response.getHeaders().getFirst(ACCESS_CONTROL_ALLOW_ORIGIN), is("*"));
        } finally {
            server.stop();
        }
    }
}
