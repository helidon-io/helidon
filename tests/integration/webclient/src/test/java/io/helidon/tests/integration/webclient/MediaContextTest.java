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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.media.common.MediaContext;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for MediaContext functionality in WebClient.
 */
public class MediaContextTest extends TestParent {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final String DEFAULT_GREETING;
    private static final JsonObject JSON_GREETING;
    private static final JsonObject JSON_NEW_GREETING;
    private static final JsonObject JSON_OLD_GREETING;

    static {
        DEFAULT_GREETING = CONFIG.get("app.greeting").asString().orElse("Hello");

        JSON_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("message", DEFAULT_GREETING + " World!")
                .build();

        JSON_NEW_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
        JSON_OLD_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", CONFIG.get("app.greeting").asString().orElse("Hello"))
                .build();
    }

    @Test
    public void testMediaSupportDefaults() throws Exception {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .build();

        client.get()
                .request(String.class)
                .thenAccept(it -> assertThat(it, is(JSON_GREETING.toString())))
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testMediaSupportWithoutDefaults() throws Exception {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .mediaContext(MediaContext.empty())
                .build();

        client.get()
                .request(String.class)
                .thenAccept(it -> fail("No reader for String should be registered!"))
                .exceptionally(ex -> {
                    assertThat(ex.getCause().getMessage(), is("No reader found for type: class java.lang.String"));
                    return null;
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testReaderRegisteredOnClient() throws Exception {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .addReader(JsonpSupport.reader())
                .build();

        client.get()
                .request(JsonObject.class)
                .thenAccept(it -> assertThat(it, is(JSON_GREETING)))
                .thenCompose(it -> client.put()
                        .path("/greeting")
                        .submit(JSON_NEW_GREETING))
                .thenAccept(it -> fail("No writer for String should be registered!"))
                .exceptionally(ex -> {
                    assertThat(ex.getCause().getMessage(),
                               is("Transformation failed!"));
                    return null;
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testWriterRegisteredOnClient() throws Exception {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .addWriter(JsonpSupport.writer())
                .build();

        client.put()
                .path("/greeting")
                .submit(JSON_NEW_GREETING)
                .thenCompose(it -> client.get().request(JsonObject.class))
                .thenAccept(it -> fail("JsonReader should not be registered!"))
                .exceptionally(ex -> {
                    assertThat(ex.getCause().getMessage(),
                               is("No reader found for type: interface javax.json.JsonObject"));
                    return null;
                })
                .thenCompose(it -> client.put().path("/greeting").submit(JSON_OLD_GREETING)) //Cleanup
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testRequestSpecificReader() throws Exception {
        WebClient client = WebClient.builder()
                .baseUri("http://localhost:" + webServer.port() + "/greet")
                .build();

        client.get()
                .request(JsonObject.class)
                .thenAccept(it -> fail("JsonObject should not have been handled."))
                .thenCompose(it -> {
                    WebClientRequestBuilder requestBuilder = client.get();
                    requestBuilder.readerContext().registerReader(JsonpSupport.reader());
                    return requestBuilder.request(JsonObject.class);
                })
                .thenAccept(jsonObject -> assertThat(jsonObject.getString("message"), is(DEFAULT_GREETING + " World!")))
                .thenCompose(it -> client.get()
                        .request(JsonObject.class))
                .thenAccept(it -> fail("JsonObject should not have been handled."))
                .exceptionally(throwable -> {
                    assertThat(throwable.getCause().getMessage(),
                               is("No reader found for type: interface javax.json.JsonObject"));
                    return null;
                })
                .toCompletableFuture()
                .get();
    }

    @Test
    public void testInputStreamSameThread() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            webClient.get()
                    .request(InputStream.class)
                    .thenApply(it -> {
                        try {
                            it.readAllBytes();
                        } catch (IOException ignored) {
                        }
                        fail("This should have failed!");
                        return CompletableFuture.completedFuture(it);
                    })
                    .toCompletableFuture()
                    .get();
        });
        if (exception.getCause() instanceof IllegalStateException) {
            assertThat(exception.getCause().getMessage(),
                       is("DataChunkInputStream needs to be handled in separate thread to prevent deadlock."));
        } else {
            fail(exception);
        }
    }

    @Test
    public void testInputStreamSameThreadTestContentAs() {
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            webClient.get()
                    .request()
                    .thenCompose(it -> it.content().as(InputStream.class))
                    .thenApply(it -> {
                        try {
                            it.readAllBytes();
                        } catch (IOException ignored) {
                        }
                        fail("This should have failed!");
                        return CompletableFuture.completedFuture(it);
                    })
                    .toCompletableFuture()
                    .get();
        });
        if (exception.getCause() instanceof IllegalStateException) {
            assertThat(exception.getCause().getMessage(),
                       is("DataChunkInputStream needs to be handled in separate thread to prevent deadlock."));
        } else {
            fail(exception);
        }
    }

    @Test
    public void testInputStreamDifferentThread() throws IOException, ExecutionException, InterruptedException {
        InputStream is = webClient.get()
                .request(InputStream.class)
                .toCompletableFuture()
                .get();
        assertThat(new String(is.readAllBytes()), is("{\"message\":\"Hello World!\"}"));
    }

    @Test
    public void testInputStreamDifferentThreadContentAs() throws IOException, ExecutionException, InterruptedException {
        InputStream is = webClient.get()
                .request()
                .thenCompose(it -> it.content().as(InputStream.class))
                .toCompletableFuture()
                .get();
        assertThat(new String(is.readAllBytes()), is("{\"message\":\"Hello World!\"}"));
    }


}
