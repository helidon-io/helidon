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
 *
 */
package io.helidon.cors;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.media.common.MediaSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.Routing;
import io.helidon.webserver.Service;
import io.helidon.webserver.WebServer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class SimpleTest {

    private static WebServer server;
    private static WebClient client;

    private static final CORSSupport.Builder SIMPLE_BUILDER = CORSSupport.builder();
    private static final Supplier<? extends Service> GREETING_BUILDER = () -> new GreetService();

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        Routing.Builder routingBuilder = TestUtil.prepRouting();
        routingBuilder.register(SIMPLE_BUILDER);
        routingBuilder.register("/greet", GREETING_BUILDER);

        server = TestUtil.startServer(0, routingBuilder);
        client = WebClient.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .build();
    }

    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(server);
    }

    @Test
    public void testSimple() throws Exception {

        WebClientResponse response = client.get()
                .path("/")
                .accept(MediaType.TEXT_PLAIN)
                .request()
                .toCompletableFuture()
                .get();

        String msg = response.content().as(String.class).toCompletableFuture().get();
        Http.ResponseStatus result = response.status();

        assertThat(result.code(), is(Http.Status.OK_200.code()));
    }
}
