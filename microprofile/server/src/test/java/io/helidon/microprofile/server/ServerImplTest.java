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

package io.helidon.microprofile.server;

import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;

import io.helidon.common.configurable.ThreadPoolSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Unit test for {@link ServerImpl}.
 */
class ServerImplTest {
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
    void testCustomExecutorService() {
        Server server = Server.builder()
                .port(0)
                .addApplication("/app1", new TestApplication1())
                .addApplication(JaxRsApplication.builder()
                                        .contextRoot("/app2")
                                        .application(new TestApplication2())
                                        .executorService(execService("custom-2-"))
                                        .build())
                .build();

        server.start();

        try {
            WebTarget target = client.target("http://localhost:" + server.port());

            String first = target.path("/app1/test1").request().get(String.class);
            String second = target.path("/app2/test2").request().get(String.class);

            assertThat(first, startsWith("test1: helidon-"));
            assertThat(second, startsWith("test2: custom-2-"));
        } finally {
            server.stop();
        }
    }

    private ExecutorService execService(String prefix) {
        return ThreadPoolSupplier.builder()
                .threadNamePrefix(prefix)
                .corePoolSize(1)
                .build()
                .get();
    }

    @Test
    void testTwoApps() {
        Server server = Server.builder()
                .port(0)
                .addApplication("/app1", new TestApplication1())
                .addApplication("/app2/", new TestApplication2())       // trailing slash ignored
                .build();

        server.start();

        try {
            WebTarget target = client.target("http://localhost:" + server.port());

            String first = target.path("/app1/test1").request().get(String.class);
            String second = target.path("/app2/test2").request().get(String.class);

            assertThat(first, startsWith("test1: helidon-"));
            assertThat(second, startsWith("test2: helidon-"));
        } finally {
            server.stop();
        }
    }

    private final class TestApplication1 extends Application {
        @Override
        public Set<Object> getSingletons() {
            return Set.of(new TestResource1());
        }
    }

    private final class TestApplication2 extends Application {
        @Override
        public Set<Object> getSingletons() {
            return Set.of(new TestResource2());
        }
    }

    @Path("/test1")
    public final class TestResource1 {
        @GET
        public String getIt() {
            return "test1: " + Thread.currentThread().getName();
        }
    }

    @Path("/test2")
    public final class TestResource2 {
        @GET
        public String getIt() {
            return "test2: " + Thread.currentThread().getName();
        }
    }
}
