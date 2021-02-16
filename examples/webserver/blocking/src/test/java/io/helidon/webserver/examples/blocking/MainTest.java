/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.webserver.examples.blocking;

import java.net.http.HttpClient;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.json.JsonObject;

import io.helidon.config.Config;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainTest {
    private static SleepingServer sleepingServer;
    private static WebClient sleepingClient;
    private static BlockingServer blockingServer;
    private static WebClient blockingClient;
    private static ReactiveServer reactiveServer;
    private static WebClient reactiveClient;

    @BeforeAll
    static void initTest() {
        Config config = Config.create();
        sleepingServer = new SleepingServer();
        int sleepingPort = sleepingServer.start(config);
        // now we can create a client to be used by other server
        WebClient webClient = WebClient.builder()
                .baseUri("http://localhost:" + sleepingPort + "/sleep")
                .keepAlive(true)
                .build();

        // client for blocking calls
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(Executors.newFixedThreadPool(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();

        blockingServer = new BlockingServer();
        int blockingPort = blockingServer.start(config, client, sleepingPort);
        reactiveServer = new ReactiveServer();
        int reactivePort = reactiveServer.start(config, webClient);

        sleepingClient = WebClient.builder()
                .baseUri("http://localhost:" + sleepingPort)
                .build();
        blockingClient = WebClient.builder()
                .baseUri("http://localhost:" + blockingPort)
                .addMediaSupport(JsonpSupport.create())
                .build();
        reactiveClient = WebClient.builder()
                .baseUri("http://localhost:" + reactivePort)
                .addMediaSupport(JsonpSupport.create())
                .build();
    }

    @AfterAll
    static void destroyTest() {
        if (sleepingServer != null) {
            sleepingServer.stop();
        }
        if (blockingServer != null) {
            blockingServer.stop();
        }
        if (reactiveServer != null) {
            reactiveServer.stop();
        }
    }

    @Test
    void testReactiveEndpoint() {
        JsonObject response = reactiveClient.get()
                .path("/call")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        assertEquals("OK", response.getString("status"));
    }

    @Test
    void testBlockingEndpoint() {
        JsonObject response = blockingClient.get()
                .path("/call")
                .request(JsonObject.class)
                .await(10, TimeUnit.SECONDS);

        assertEquals("OK", response.getString("status"));
    }

    @Test
    void testSleepService() {
        String response = sleepingClient.get()
                .path("/sleep")
                .request(String.class)
                .await(10, TimeUnit.SECONDS);

        assertEquals("OK", response);
    }
}