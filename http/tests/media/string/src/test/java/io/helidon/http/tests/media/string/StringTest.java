/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.http.tests.media.string;

import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.HttpMediaType;
import io.helidon.http.HttpMediaTypes;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@ServerTest
class StringTest {
    private static final HttpMediaType TEXT_ISO_8859_2 = HttpMediaType.create(MediaTypes.TEXT_PLAIN)
            .withCharset("ISO-8859-2");
    private static final Header ISO_8859_CONTENT_TYPE = HeaderValues.create(HeaderNames.CONTENT_TYPE,
                                                                            TEXT_ISO_8859_2.text());
    private static final String UTF_8_TEXT = "český řízný text";

    private final Http1Client client;

    StringTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void route(HttpRouting.Builder router) {
        router.get("/utf8", (req, res) -> res.send(UTF_8_TEXT))
                .get("/iso8859_2", (req, res) -> res.header(ISO_8859_CONTENT_TYPE).send(UTF_8_TEXT))
                .post("/request",
                      (req, res) -> res.send(req.content().as(String.class) + ":" + req.headers().contentType()
                              .map(HttpMediaType::text).orElse("NONE")));
    }

    @Test
    void testGetUtf8() {
        Http1ClientResponse response = client.get("/utf8")
                .request();

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type plain/text; charset=UTF-8",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaTypes.PLAINTEXT_UTF_8))),
                () -> assertThat(response.as(String.class), is(UTF_8_TEXT)));
    }

    @Test
    void testGetIso8859_2() {
        Http1ClientResponse response = client.get("/iso8859_2")
                .request();

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type plain/text; charset=ISO_8859_2",
                                 response.headers().contentType(),
                                 is(Optional.of(TEXT_ISO_8859_2))),
                () -> assertThat(response.as(String.class), is(UTF_8_TEXT)));
    }

    @Test
    void testPostUtf8NoContentType() {
        Http1ClientResponse response = client.method(Method.POST)
                .uri("/request")
                .submit(UTF_8_TEXT);

        assertAll(
                () -> assertThat(response.status(), is(Status.OK_200)),
                () -> assertThat("Should contain content type plain/text; charset=UTF-8",
                                 response.headers().contentType(),
                                 is(Optional.of(HttpMediaTypes.PLAINTEXT_UTF_8))),
                () -> assertThat(response.as(String.class), is(UTF_8_TEXT + ":" + HttpMediaTypes.PLAINTEXT_UTF_8.text())));
    }
}
