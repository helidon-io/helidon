/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.gh6845;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import io.helidon.common.http.MediaType;
import io.helidon.common.reactive.Single;
import io.helidon.media.multipart.MultiPartSupport;
import io.helidon.media.multipart.ReadableBodyPart;
import io.helidon.webclient.WebClient;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * See the related <a href="https://github.com/helidon-io/helidon/issues/6845">issue</a>.
 */
class ChunkReleaseTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void startTheServer() {
        webServer = Main.startServer().start().await(10, TimeUnit.SECONDS);
        webClient = WebClient.builder()
                             .baseUri("http://localhost:" + webServer.port())
                             .addMediaSupport(MultiPartSupport.create())
                             .build();
    }

    @AfterAll
    public static void stopServer() {
        webServer.shutdown().await(10, TimeUnit.SECONDS);
    }

    @Test
    void testReleasing() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        webClient.get()
                 .accept(MediaType.MULTIPART_FORM_DATA)
                 .submit()
                 .flatMap(response -> response.content().asStream(ReadableBodyPart.class))
                 .flatMap(part -> part.content().as(InputStream.class).observeOn(executor))
                 .forEach(is -> {
                     try {
                         is.readAllBytes();
                     } catch (IOException e) {
                         throw new RuntimeException(e);
                     }
                 })
                 .onErrorResumeWithSingle(th -> {
                     if (th.getMessage().equals("Invalid state: END_MESSAGE")) {
                         // work-around for https://github.com/helidon-io/helidon/issues/6828
                         return Single.empty();
                     }
                     return Single.error(th);
                 })
                 .await();
    }
}
