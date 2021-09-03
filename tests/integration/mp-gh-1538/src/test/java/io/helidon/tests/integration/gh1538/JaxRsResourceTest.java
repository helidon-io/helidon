/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.gh1538;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import io.helidon.microprofile.server.Server;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JaxRsResourceTest {
    private static Server server;
    private static Client client;
    private static WebTarget target;

    @BeforeAll
    static void initClass() {
        server = Server.create(JaxRsApplication.class).start();
        client = ClientBuilder.newClient();
        target = client.target("http://localhost:" + server.port() + "/test");
    }

    @AfterAll
    static void destroyClass() {
        if (null != server) {
            server.stop();
        }
        if (null != client) {
            client.close();
        }
    }

    @Test
    @Order(1)
    void testSync() {
        target.path("/sync")
                .request()
                .get(String.class);
    }

    @Test
    @Order(2)
    void testAsync() {
        target.path("/async")
                .request()
                .get(String.class);
    }

    @Test
    // this method must be after the async test, as the threads are not created before it
    @Order(3)
    void testThreads() {
        int countOfJerseyServer = 0;
        int countOfJerseyServerAsync = 0;
        int countOfDefaultJersey = 0;

        // now make sure the threads are as expected - array quite large, to fit all threads
        Thread[] threads = new Thread[100];
        Thread.enumerate(threads);
        for (Thread thread : threads) {
            if (null == thread) {
                break;
            }
            String threadName = thread.getName();
            // see microprofile-config.properties - this is an explicit prefix
            if (threadName.startsWith("gh-1538-")) {
                countOfJerseyServer++;
            } else if (threadName.startsWith("async-gh-1538-")) {
                countOfJerseyServerAsync++;
            } else if (threadName.startsWith("jersey-server-managed-async-executor-")) {
                countOfDefaultJersey++;
            }
            // for troubleshooting:
//           else {
//                System.out.println(threadName);
//            }
        }

        assertThat("We should replace default async executor with a custom one", countOfDefaultJersey, is(0));
        assertThat("We should use our configured server threads", countOfJerseyServer, greaterThan(0));
        assertThat("We should use our configured server async threads", countOfJerseyServerAsync, greaterThan(0));
    }
}