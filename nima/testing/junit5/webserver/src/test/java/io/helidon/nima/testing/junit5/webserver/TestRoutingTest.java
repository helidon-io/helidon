/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.nima.testing.junit5.webserver;

import java.security.Principal;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.LinkedList;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@RoutingTest
class TestRoutingTest {
    private static final String ENTITY = "some nice entity";
    private static final String ADMIN_ENTITY = "admin entity";

    private final DirectClient client;

    TestRoutingTest(DirectClient client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder router) {
        router.get("/get", (req, res) -> res.send(ENTITY))
                .post("/post", (req, res) -> {
                    String requestEntity = req.content().as(String.class);
                    if (ENTITY.equals(requestEntity)) {
                        res.status(Http.Status.CREATED_201);
                    } else {
                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500);
                    }
                    res.send();
                })
                .get("/name", (req, res) -> {
                    String name = req.remotePeer().tlsPrincipal().map(Principal::getName).orElse(null);
                    if (name == null) {
                        res.status(Http.Status.BAD_REQUEST_400).send("Expected client principal");
                    } else {
                        res.send(name);
                    }
                })
                .get("/certs", (req, res) -> {
                    Certificate[] certs = req.remotePeer().tlsCertificates().orElse(null);
                    if (certs == null) {
                        res.status(Http.Status.BAD_REQUEST_400).send("Expected client certificate");
                    } else {
                        List<String> certDefs = new LinkedList<>();
                        for (Certificate cert : certs) {
                            if (cert instanceof X509Certificate x509) {
                                certDefs.add("X.509:" + x509.getSubjectX500Principal().getName());
                            } else {
                                certDefs.add(cert.getType());
                            }
                        }

                        res.send(String.join("|", certDefs));
                    }
                });
    }

    @SetUpRoute("admin")
    static void adminRouting(HttpRouting.Builder router) {
        router.get("/get", (req, res) -> res.send(ADMIN_ENTITY));
    }

    @Test
    void testGet() {
        String response = client.get("/get")
                .request()
                .as(String.class);

        assertThat(response, is(ENTITY));
    }

    @Test
    void testInjectGet(@Socket("admin") DirectClient client) {
        String response = client.get("/get")
                .request()
                .as(String.class);

        assertThat(response, is(ADMIN_ENTITY));
    }

    @Test // Test Explicit @default route
    void testInjectDefaultGet(@Socket("@default") DirectClient client) {
        String response = client.get("/get")
                .request()
                .as(String.class);

        assertThat(response, is(ENTITY));
    }

    @Test
    void testPost() {
        Http1ClientResponse response = client.method(Http.Method.POST)
                .uri("/post")
                .submit(ENTITY);

        assertThat(response.status(), is(Http.Status.CREATED_201));
    }

    @Test
    void testMutualTlsPrincipal() {
        String principal = "Custom principal name";
        client.clientTlsPrincipal(new TestPrincipal(principal));
        Http1ClientResponse response = client.method(Http.Method.GET)
                .uri("/name")
                .request();

        assertAll(() -> assertThat(response.status(), is(Http.Status.OK_200)),
                  () -> assertThat(response.as(String.class), is(principal)));
    }

    private static final class TestPrincipal implements Principal {
        private final String name;

        private TestPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
