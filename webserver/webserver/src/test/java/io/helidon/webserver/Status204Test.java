/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import io.helidon.common.http.Http;
import io.helidon.webclient.WebClient;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests specific header expectation from 204 NO CONTENT status code together with {@link io.helidon.webclient.WebClient}.
 */
public class Status204Test {

    private WebServer server;

    @BeforeEach
    public void createAndStartServer() throws Exception {
        this.server = Routing.builder()
               .get(((req, res) -> {
                   res.send("test");
               }))
               .put(Handler.create(String.class, (req, res, entity) -> {
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
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:" + server.port())
                .build();

        webClient.put()
                .submit("test call")
                .thenAccept(it -> assertThat(it.status(), is(Http.Status.NO_CONTENT_204)))
                .thenCompose(it -> webClient.get()
                        .request(String.class))
                .thenAccept(it -> assertThat(it, is("test")))
                .toCompletableFuture()
                .get();

    }
}
