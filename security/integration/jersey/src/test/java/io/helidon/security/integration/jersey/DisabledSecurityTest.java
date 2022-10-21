/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.security.integration.jersey;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.security.Principal;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.Subject;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that Jersey binding works.
 */
public class DisabledSecurityTest {
    private static Client client;
    private static WebServer server;
    private static WebTarget baseTarget;

    @BeforeAll
    public static void initClass() throws Throwable {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("disabledSecurity.yaml"))
                .build();

        server = WebServer.builder(Routing.builder()
                                           .register(JerseySupport.builder()
                                                             .register(MyResource.class)
                                                             .register(new SecurityFeature(Security.create(config.get("security"
                                                             ))))
                                                             .register((ExceptionMapper<Exception>) exception -> {
                                                                 exception.printStackTrace();
                                                                 return Response.serverError().build();
                                                             })
                                                             .build())
                                           .build())
                .build()
                .start()
                .get(10, TimeUnit.SECONDS);

        client = ClientBuilder.newClient();
        URI baseUri = UriBuilder.fromUri("http://localhost/").port(server.port()).build();
        baseTarget = client.target(baseUri);
    }

    @AfterAll
    public static void destroyClass() throws InterruptedException {
        client.close();

        CountDownLatch cdl = new CountDownLatch(1);
        server.shutdown().whenComplete((webServer, throwable) -> cdl.countDown());
        cdl.await();
    }

    @Test
    public void testEmptySecurityContextInjection() {
        Response response = baseTarget
                .path("/")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("hello EMPTY USER"));
    }

    @Path("/")
    public static class MyResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String empty(@Context SecurityContext context) {
            return "hello " +
                    context.user()
                            .map(Subject::principal)
                            .map(Principal::getName)
                            .orElse("EMPTY USER");
        }

    }
}

