/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver.netty;

import java.net.HttpURLConnection;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.helidon.common.http.Http;
import io.helidon.webserver.Handler;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests specific header expectation from 204 NO CONTENT status code together with {@link HttpURLConnection} based JAX-RS client.
 */
public class Status204Test {

    private WebServer server;

    @BeforeEach
    public void createAndStartServer() throws Exception {
        this.server = Routing.builder()
               .get(((req, res) -> {
                   res.send("test");
               }))
               .put(Handler.of(String.class, (req, res, entity) -> {
                   res.status(Http.Status.NO_CONTENT_204).send();
               }))
               .createServer();
        this.server.start().toCompletableFuture().get();
    }

    @AfterEach
    public void stopServer() throws Exception {
        this.server.shutdown().toCompletableFuture().get();
    }

    @Test
    public void callPutAndGet() throws Exception {
        WebTarget target = ClientBuilder.newClient()
                                        .target("http://localhost:" + server.port());
        Response response = target.request().put(Entity.entity("test call", MediaType.TEXT_PLAIN));
        assertEquals(204, response.getStatus());
        String s = target.request().get(String.class);
        assertEquals("test", s);
    }
}
