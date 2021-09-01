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

package io.helidon.examples.integrations.microstream.greetings.se;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

public class MicrostreamExampleGreetingsSeTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @TempDir
    static Path tempDir;

    @BeforeAll
    public static void startServer() throws Exception {
        System.setProperty("microstream.storage-directory", tempDir.toString());

        webServer = Main.startServer();

        webClient = WebClient.builder().baseUri("http://localhost:" + webServer.port())
                .addMediaSupport(JsonpSupport.create()).build();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testExample() throws ExecutionException, InterruptedException {
        webClient
        .get()
        .path("/greet/Joe")
        .request(JsonObject.class)
        .thenAccept(jsonObject -> Assertions.assertEquals("Hello Joe!", jsonObject.getString("message")))
        .toCompletableFuture()
        .get();

        webClient
        .get()
        .path("/greet/logs")
        .request(JsonArray.class)
        .thenAccept(jsonArray -> {
            Assertions.assertEquals("Joe", jsonArray.get(0).asJsonObject().getString("name"));
            Assertions.assertNotNull(jsonArray.get(0).asJsonObject().getString("time"));
        })
        .toCompletableFuture()
        .get();
    }
}
