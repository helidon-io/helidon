/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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
package io.helidon.webclient.tests;

import java.io.InputStream;
import java.util.Collections;

import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.UnsupportedTypeException;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.WebServer;

import jakarta.json.Json;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for MediaContext functionality in WebClient.
 */
@TestMethodOrder(OrderAnnotation.class)
public class MediaContextTest extends TestParent {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final String DEFAULT_GREETING;
    private static final JsonObject JSON_GREETING;
    private static final JsonObject JSON_NEW_GREETING;

    static {
        DEFAULT_GREETING = CONFIG.get("app.greeting").asString().orElse("Hello");

        JSON_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("message", DEFAULT_GREETING + " World!")
                .build();

        JSON_NEW_GREETING = JSON_BUILDER.createObjectBuilder()
                .add("greeting", "Hola")
                .build();
    }

    MediaContextTest(WebServer server) {
        super(server);
    }

    @Test
    @Order(1)
    public void testInputStream() {
        try (Http1ClientResponse res = client.get().request()) {
            InputStream is = res.inputStream();
            assertAll(
                    () -> assertThat(res.status(), is(Status.OK_200)),
                    () -> assertThat(new String(is.readAllBytes()), is("{\"message\":\"Hello World!\"}"))
            );
        }
    }

    @Test
    @Order(2)
    public void testMediaSupportDefaults() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .build();

        String greeting = client.get().requestEntity(String.class);
        assertThat(greeting, is(JSON_GREETING.toString()));
    }

    @Test
    @Order(3)
    public void testMediaSupportWithoutDefaults() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .mediaContext(MediaContext.builder()
                                      .registerDefaults(false)
                                      .mediaSupportsDiscoverServices(false)
                        .build())
                .build();

        UnsupportedTypeException ex = assertThrows(UnsupportedTypeException.class, () ->
                client.get().request(String.class).entity());
        assertThat(ex.getMessage(), startsWith("No client response media support for class"));
    }

    @Test
    @Order(4)
    @Disabled("https://github.com/helidon-io/helidon/issues/7205")
    public void testReaderRegisteredOnClient() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .mediaContext(MediaContext.builder()
                        //.addMediaReader(JsonpSupport.create().reader())
                        .mediaSupportsDiscoverServices(false)
                        .build())
                .build();

        JsonObject jsonObject = client.get().requestEntity(JsonObject.class);
        assertThat(jsonObject, is(not(nullValue())));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                client.put()
                        .path("/greeting")
                        .submit(JSON_NEW_GREETING)
                        .close());
        assertThat(ex.getMessage(), startsWith("No client request media writer for class"));
    }

    @Test
    @Order(5)
    @Disabled("https://github.com/helidon-io/helidon/issues/7205")
    public void testWriterRegisteredOnClient() {
        Http1Client client = Http1Client.builder()
                .baseUri("http://localhost:" + server.port() + "/greet")
                .mediaContext(MediaContext.builder()
                        //.addMediaWriter(JsonpSupport.create().writer())
                        .mediaSupportsDiscoverServices(false)
                        .build())
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            try (Http1ClientResponse response = client.put()
                    .path("/greeting")
                    .submit(JSON_NEW_GREETING)) {
                response.as(JsonObject.class);
            }
        });
        assertThat(ex.getMessage(), startsWith("No client response media support for interface"));
    }
}
