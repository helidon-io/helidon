/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ExceptionMapper;

import io.helidon.config.Config;
import io.helidon.security.Security;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.jersey.JerseySupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Test that query params can be sent and resolved as headers for security.
 */
public class ExtractQueryParamsTest {

    private static final String USERNAME = "assdlakdfknkasdfvsadfasf";
    private static Client client;
    private static WebServer server;
    private static WebTarget baseTarget;

    @BeforeAll
    public static void initClass() throws Throwable {
        Config config = Config.create();
        Security security = Security.create(config.get("security"));
        SecurityFeature feature = SecurityFeature.builder(security)
                .config(config.get("security.jersey"))
                .build();

        server = Routing.builder()
                .register(JerseySupport.builder()
                                  .register(BindingTest.MyResource.class)
                                  .register(TestResource1.class)
                                  .register(new TestResource2())
                                  .register(feature)
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
        baseTarget = client.target(UriBuilder.fromUri("http://localhost/").port(server.port()).build());
    }

    @AfterAll
    public static void destroyClass() throws InterruptedException {
        client.close();

        CountDownLatch cdl = new CountDownLatch(1);
        server.shutdown().whenComplete((webServer, throwable) -> cdl.countDown());
        cdl.await();
    }

    @Test
    public void testBasicHeader() {
        Response response = baseTarget.path("/test2")
                .request()
                .header("x-user", USERNAME)
                .get();

        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), containsString(USERNAME));
    }

    @Test
    public void testBasicQuery() {
        Response response = baseTarget.path("/test2")
                .queryParam("basicAuth", USERNAME)
                .request()
                .get();

        assertThat(response.getStatus(), is(200));
        assertThat(response.readEntity(String.class), containsString(USERNAME));
    }

    @Test
    public void testBasicFails() {
        Response response = baseTarget.path("/test2")
                .queryParam("wrong", USERNAME)
                .request()
                .get();

        assertThat(response.getStatus(), is(401));
    }
}
