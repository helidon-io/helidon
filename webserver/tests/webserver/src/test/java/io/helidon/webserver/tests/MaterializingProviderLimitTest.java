/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.tests;

import java.nio.charset.StandardCharsets;

import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.http.media.jsonb.JsonbSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MaterializingProviderLimitTest {
    private static final int DEFAULT_LIMIT = 1024 * 1024;
    private static final String ACCEPTED_VALUE = "A".repeat(DEFAULT_LIMIT - 12);
    private static final byte[] ACCEPTED_JSON = ("{\"value\":\"" + ACCEPTED_VALUE + "\"}")
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] REJECTED_JSON = ("{\"value\":\"" + "A".repeat(DEFAULT_LIMIT + 1024) + "\"}")
            .getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @EnumSource(Provider.class)
    void oversizedRequestRetains413(Provider provider) {
        MediaContext context = mediaContext(provider);
        WebServer server = server(context);
        Http1Client client = client(server, context);
        try {
            assertThat(ACCEPTED_JSON.length, is(DEFAULT_LIMIT));
            try (var response = client.post("/entity")
                    .header(HeaderNames.CONTENT_TYPE, "application/json")
                    .submit(ACCEPTED_JSON)) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(String.class), is(Integer.toString(ACCEPTED_VALUE.length())));
            }
            try (var response = client.post("/entity")
                    .header(HeaderNames.CONTENT_TYPE, "application/json")
                    .submit(REJECTED_JSON)) {
                assertThat(response.status(), is(Status.REQUEST_ENTITY_TOO_LARGE_413));
            }
        } finally {
            client.closeResource();
            server.stop();
        }
    }

    @ParameterizedTest
    @EnumSource(Provider.class)
    void oversizedResponseRetainsClientLimitException(Provider provider) {
        MediaContext context = mediaContext(provider);
        WebServer server = server(context);
        Http1Client client = client(server, context);
        try {
            try (var response = client.get("/accepted").request()) {
                assertThat(response.status(), is(Status.OK_200));
                assertThat(response.as(Payload.class).value.length(), is(ACCEPTED_VALUE.length()));
            }
            try (var response = client.get("/rejected").request()) {
                assertThat(response.status(), is(Status.OK_200));
                IllegalStateException exception = assertThrows(IllegalStateException.class,
                                                               () -> response.as(Payload.class));
                assertThat(exception.getMessage(), containsString("Maximum materialized entity size exceeded"));
                assertThat(response.status(), is(Status.OK_200));
            }
        } finally {
            client.closeResource();
            server.stop();
        }
    }

    private static MediaContext mediaContext(Provider provider) {
        return MediaContext.builder()
                .mediaSupportsDiscoverServices(false)
                .addMediaSupport(switch (provider) {
                    case JSON_B -> JsonbSupport.create();
                    case JACKSON -> JacksonSupport.create();
                })
                .build();
    }

    private static WebServer server(MediaContext context) {
        return WebServer.builder()
                .host("127.0.0.1")
                .port(0)
                .mediaContext(context)
                .routing(rules -> rules
                        .post("/entity",
                              (req, res) -> res.send(Integer.toString(req.content().as(Payload.class).value.length())))
                        .get("/accepted", (_, res) -> {
                            res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                            res.send(ACCEPTED_JSON);
                        })
                        .get("/rejected", (_, res) -> {
                            res.headers().set(HeaderNames.CONTENT_TYPE, "application/json");
                            res.send(REJECTED_JSON);
                        }))
                .build()
                .start();
    }

    private static Http1Client client(WebServer server, MediaContext context) {
        return Http1Client.builder()
                .baseUri("http://127.0.0.1:" + server.port())
                .mediaContext(context)
                .build();
    }

    private enum Provider {
        JSON_B,
        JACKSON
    }

    public static final class Payload {
        public String value;
    }
}
