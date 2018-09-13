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

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;
import javax.inject.Provider;
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
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test that Jersey binding works.
 */
public class BindingTest {
    private static Client client;
    private static WebServer server;
    private static WebTarget baseTarget;

    @BeforeAll
    public static void initClass() throws Throwable {
        Config config = Config.builder()
                .sources(ConfigSources.classpath("bind.yaml"))
                .build();



        server = Routing.builder()
                .register(JerseySupport.builder()
                                  .register(MyResource.class)
                                  .register(TestResource1.class)
                                  .register(new TestResource2())
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
    @Disabled("We must use random ports for tests, not sure how to handle that in web target injection yet.")
    public void testInjection() {
        String username = "SAFAxvdfaLDKJFSlkJSS";
        // call TestResource1 with x-user header
        TestResource1.TransferObject response = baseTarget.path("/test1")
                .request()
                .header("x-user", username)
                .get(TestResource1.TransferObject.class);

        // field should be a proxy
        assertThat(response.getFieldClass(), response.isField(), is(true));
        // parameter should NOT be a proxy - we may need to "leak" security context from request scope!!!
        assertThat(response.getParamClass(), response.isParam(), is(false));
        assertThat(response.getSubject(), containsString(username));
    }

    @Test
    public void testBindToJersey() throws IOException {
        Response response = baseTarget
                .path("/")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat(entity, is("hello"));

        //this should fail
        try {
            client.target("http://localhost:" + server.port())
                    .path("/deny")
                    .request()
                    .get(String.class);
            fail("The deny path should have been forbidden by authorization provider");
        } catch (ForbiddenException ignored) {
            //this is expected
        }
    }

    @Test
    public void testContextProvider() {
        Response response = baseTarget
                .path("/scProvider")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected Provider<SecurityContext> was null", entity, containsString("null=false"));
        assertThat("Injected Provider<SecurityContext> returned null", entity, containsString("contentNull=false"));
        // this is still a proxy, not sure why
        //        assertThat("Injected Provider<SecurityContext> is a proxy", entity, containsString("proxy=false"));
    }

    @Test
    public void testContextInstance() {
        Response response = baseTarget
                .path("/scInstance")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected SecurityContext was null", entity, containsString("null=false"));
        assertThat("Injected SecurityContext was a proxy", entity, containsString("proxy=false"));
    }

    @Test
    public void testContextParameter() {
        Response response = baseTarget
                .path("/scParam")
                .request()
                .get();

        assertThat(response.getStatus(), is(200));

        String entity = response.readEntity(String.class);
        assertThat("Injected SecurityContext was null", entity, containsString("null=false"));
        assertThat("Injected SecurityContext was a proxy", entity, containsString("proxy=false"));
    }

    @Path("/")
    public static class MyResource {
        @Inject
        private Provider<SecurityContext> scProvider;

        @Context
        private SecurityContext scInstance;

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scParam")
        public String checkScParam(@Context SecurityContext context) {
            if (null == context) {
                return "null=true";
            }
            return "null=false,proxy=" + Proxy.isProxyClass(context.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scProvider")
        public String checkScProvider() {
            if (null == scProvider) {
                return "null=true";
            }
            SecurityContext context = scProvider.get();
            if (null == context) {
                return "null=false,contentNull=true";
            }

            return "null=false,contentNull=false,proxy=" + Proxy.isProxyClass(context.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        @Path("scInstance")
        public String checkScInstance() {
            if (null == scInstance) {
                return "null=true";
            }
            return "null=false,proxy=" + Proxy.isProxyClass(scInstance.getClass());
        }

        @GET
        @Produces(MediaType.TEXT_PLAIN)
        public String getIt() {
            scProvider.get().isUserInRole("test");
            return "hello";
        }

        @GET
        @Path("deny")
        public String denyIt() {
            return "shouldNotGet";
        }
    }
}

