/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;
import io.helidon.webclient.WebClient;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;




public class FormParamsSupportTest {

    private static WebServer testServer;
    private static WebClient webClient;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        testServer = WebServer.create(ServerConfiguration.builder()
                        .port(0)
                        .build(),
                    Routing.builder()
                        .register(FormParamsSupport.create())
                        .put("/params", (req, resp) -> {
                            req.content().as(FormParams.class).thenAccept(fp ->
                                    resp.send(fp.toMap().toString()));
                        })
                        .build())
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        webClient = WebClient.builder()
                .baseUri("http://localhost:" + testServer.port())
                .build();
    }

    @AfterAll
    public static void shutdown() {
        testServer.shutdown();
    }

    @Test
    public void urlEncodedTest() throws Exception {
        webClient.put()
                .path("/params")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .submit("key1=val+1&key2=val2_1&key2=val2_2", String.class)
                .thenAccept(it -> {
                    assertThat(it, containsString("key1=[val 1]"));
                    assertThat(it, containsString("key2=[val2_1, val2_2]"));
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void plainTextTest() throws Exception{
        webClient.put()
                .path("/params")
                .contentType(MediaType.TEXT_PLAIN)
                .submit("key1=val 1\nkey2=val2_1\nkey2=val2_2", String.class)
                .thenAccept(it -> {
                    assertThat(it, containsString("key1=[val 1]"));
                    assertThat(it, containsString("key2=[val2_1, val2_2]"));
                })
                .toCompletableFuture()
                .get();
    }

}
