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

package io.helidon.tests.integration.webclient;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.media.common.MediaSupport;
import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.media.jsonp.common.JsonProcessing;
import io.helidon.security.Security;
import io.helidon.security.SecurityContext;
import io.helidon.security.providers.httpauth.HttpBasicAuthProvider;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;
import io.helidon.webclient.spi.WebClientService;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;

/**
 * Parent class for integration tests.
 */
class TestParent {

    protected static final Config CONFIG = Config.create();

    protected static WebServer webServer;
    protected static WebClient webClient;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer().toCompletableFuture().get();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }

        webClient = createNewClient();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    protected static WebClient createNewClient(WebClientService... clientServices) {
        Security security = Security.builder()
                .addProvider(HttpBasicAuthProvider.builder().build())
                .build();

        SecurityContext securityContext = security.createContext("unit-test");

        Context context = Context.builder().id("unit-test").build();
        context.register(securityContext);

        WebClient.Builder builder = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .config(CONFIG.get("client"))
                .context(context)
                .addMediaService(JsonProcessing.create());

//        //---------------------------------------------------------------
//        //Use registry with defaults (default behavior)
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .build();
//
//        //Use registry with defaults + with include stacktraces
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .mediaSupport(MediaSupport.builder()
//                                       .includeStackTraces(true)
//                                       .build())
//                .build();
//
//        //Use empty registry
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .mediaSupport(MediaSupport.empty())
//                .build();
//        //-----------------------------------------------------
//        //Use registry with defaults + json reader in client
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .addReader(JsonProcessing.reader())
//                .build();
//
//        //Use registry with defaults + json writer in client
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .addWriter(JsonProcessing.writer())
//                .build();
//
//        //Use registry with defaults + json support in client
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .addMediaService(JsonProcessing.create())
//                .build();
//
//        //-----------------------------------------------------------
//        //Use registry with defaults + json reader in registry
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .mediaSupport(MediaSupport.builder()
//                                       .addReader(JsonProcessing.reader())
//                                       .build())
//                .build();
//
//        //Use registry with defaults + json writer in registry
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .mediaSupport(MediaSupport.builder()
//                                       .addWriter(JsonProcessing.writer())
//                                       .build())
//                .build();
//
//        //Use registry with defaults + json support in registry
//        WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .mediaSupport(MediaSupport.builder()
//                                       .addMediaService(JsonProcessing.create())
//                                       .build())
//                .build();
//
//        //-----------------------------------------------------------
//
//        //Advanced use-case - Demonstrates lower level API provided by reader and writer context
//        //Main API doesnt support this use-case, but it the user really wants to specify operators per request/response it is
//        // doable.
//
//        //Use registry with defaults + json writer per client request
//        WebClient webClient = WebClient.builder()
//                .baseUri("http://localhost:" + webServer.port() + "/greet")
//                .config(CONFIG.get("client"))
//                .build();
//
//        WebClientRequestBuilder requestBuilder = webClient.get();
//        requestBuilder.writerContext().registerWriter(JsonProcessing.writer());
//        requestBuilder.request();
//
//        //Use registry with defaults + json reader per client request
//        requestBuilder.readerContext().registerReader(JsonProcessing.reader());
//        requestBuilder.request(JsonObject.class)
//                .thenAccept(System.out::println);
//
//        //Use registry with defaults + json reader per client request
//        requestBuilder.request()
//                .thenCompose(it -> {
//                    MessageBodyReadableContent content = it.content();
//                    content.registerReader(JsonProcessing.reader());
//                    return content.as(JsonObject.class);
//                })
//                .thenAccept(System.out::println);

        Stream.of(clientServices).forEach(builder::register);
        return builder.build();
    }


//    public void something() {
//
//        //Use registry with defaults (default behavior)
//        WebServer.builder(Routing.builder())
//                .build();
//
//        //Use registry with defaults + with include stacktraces
//        WebServer.builder(Routing.builder())
//                .mediaRegistry(MediaSupport.builder()
//                                       .includeStackTraces(true)
//                                       .build())
//                .build();
//
//        //Use empty registry
//        WebServer.builder(Routing.builder())
//                .mediaRegistry(MediaSupport.builder()
//                                       .registerDefaults(false)
//                                       .build())
//                .build();
//
//        //-----------------------------------------------------
//        //Use registry with defaults + json reader in server
//        WebServer.builder(Routing.builder())
//                .addReader(JsonProcessing.reader())
//                .build();
//
//        //Use registry with defaults + json writer in server
//        WebServer.builder(Routing.builder())
//                .addWriter(JsonProcessing.writer())
//                .build();
//
//        //Use registry with defaults + json support in server
//        WebServer.builder(Routing.builder())
//                .addMediaService(JsonProcessing.create())
//                .build();
//
//        //-----------------------------------------------------------
//        //Use registry with defaults + json reader in registry
//        WebServer.builder(Routing.builder())
//                .mediaRegistry(MediaSupport.builder()
//                                       .addReader(JsonProcessing.reader())
//                                       .build())
//                .build();
//
//        //Use registry with defaults + json writer in registry
//        WebServer.builder(Routing.builder())
//                .mediaRegistry(MediaSupport.builder()
//                                       .addWriter(JsonProcessing.writer())
//                                       .build())
//                .build();
//
//        //Use registry with defaults + json support in registry
//        WebServer.builder(Routing.builder())
//                .mediaRegistry(MediaSupport.builder()
//                                       .addMediaService(JsonProcessing.create())
//                                       .build())
//                .build();
//
//        //-----------------------------------------------------------
//        //Advanced use-case - Demonstrates lower level API provided by reader and writer context
//        //Main API doesnt support this use-case, but it the user really wants to specify operators per request/response it is
//        // doable.
//
//        //Use registry with defaults + json reader per server request
//        WebServer.builder(Routing.builder()
//                                  .get(((req, res) -> {
//                                      MessageBodyReadableContent content = req.content();
//                                      content.registerReader(JsonProcessing.reader());
//                                      content.as(JsonObject.class)
//                                                .thenAccept(System.out::print);
//                                  })))
//                .build();
//
//        //Use registry with defaults + json writer per server response
//        WebServer.builder(Routing.builder()
//                                  .get(((req, res) -> {
//                                      res.writerContext().registerWriter(JsonProcessing.writer());
//                                      res.send(Json.createObjectBuilder()
//                                                       .add("key", "value")
//                                                       .build());
//                                  })))
//                .build();
//
//    }

}
