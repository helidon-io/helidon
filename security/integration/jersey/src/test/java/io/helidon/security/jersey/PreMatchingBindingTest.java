/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.security.jersey;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.ForbiddenException;
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
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Jersey binding works.
 */
public class PreMatchingBindingTest {
    private static Client client;
    private static WebServer server;
    private static WebTarget baseTarget;

    @BeforeAll
    public static void initClass() throws Throwable {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("pre-matching.yaml"))
                .build();

        server = Routing.builder()
                .register(JerseySupport.builder()
                                  .register(MyResource.class)
                                  .register(new SecurityFeature(Security.fromConfig(config)))
                                  .register(new ExceptionMapper<Exception>() {
                                      @Override
                                      public Response toResponse(Exception exception) {
                                          exception.printStackTrace();
                                          return Response.serverError().build();
                                      }
                                  })
                                  .build())
                .build()
                .createServer();
        CountDownLatch cdl = new CountDownLatch(1);
        AtomicReference<Throwable> th = new AtomicReference<>();
        server.start().whenComplete((webServer, throwable) -> {
            th.set(throwable);
            cdl.countDown();
        });

        cdl.await();

        if (th.get() != null) {
            throw th.get();
        }

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
    public void testPublic() {
        Response response = baseTarget
                .path("/")
                .request()
                .get();

        // this must be forbidden, as we use a pre-matching filter
        assertThat(response.getStatus(), is(401));
    }

    @Test
    public void testAuthenticated() {
        Response response = baseTarget
                .path("/")
                .request()
                .header("x-user", "jack")
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("hello jack"));
    }

    @Test
    public void testDeny() {
        //this should fail
        try {
            baseTarget
                    .path("/deny")
                    .request()
                    // we must authenticate, as we use a pre-matching filter
                    .header("x-user", "jack")
                    .get(String.class);
            fail("The deny path should have been forbidden by authorization provider");
        } catch (ForbiddenException ignored) {
            //this is expected
        }
    }

    @Path("/")
    public static class MyResource {
        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getIt(@Context SecurityContext context) {
            return "hello" +
                    context.getUser()
                            .map(Subject::getPrincipal)
                            .map(Principal::getName)
                            .map(name -> " " + name)
                            .orElse("");
        }

        @GET
        @Path("deny")
        public String denyIt() {
            return "shouldNotGet";
        }
    }
}

