/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.http.media.multipart.WriteableMultiPart;
import io.helidon.http.media.multipart.WriteablePart;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MultipartTest {
    private static final List<String> NAMES = new ArrayList<>();
    private final Http1Client client;

    MultipartTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.post("/", MultipartTest::multipartRoute);
    }

    @Test
    void testMultipleReadParts() throws IOException {
        try (Http1ClientResponse response = client.post("/")
                .queryParam("read", "true")
                .submit(WriteableMultiPart.builder()
                                .addPart(writeablePart("field_1", "a"))
                                .addPart(writeablePart("field_2", "b"))
                                .build())) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(NAMES, hasItems("field_1", "field_2"));
        }
    }

    @Test
    void testMultipleSkipReadParts() throws IOException {
        try (Http1ClientResponse response = client.post("/")
                .queryParam("read", "false")
                .submit(WriteableMultiPart.builder()
                                .addPart(writeablePart("field_1", "a"))
                                .addPart(writeablePart("field_2", "b"))
                                .build())) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(NAMES, hasItems("field_1", "field_2"));
        }
    }

    @Test
    void testMultipleReadPartsNoLength() throws IOException {
        try (Http1ClientResponse response = client.post("/")
                .queryParam("read", "true")
                .submit(WriteableMultiPart.builder()
                                .addPart(writeablePartNoLength("field_1", "a"))
                                .addPart(writeablePartNoLength("field_2", "b"))
                                .build())) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(NAMES, hasItems("field_1", "field_2"));
        }
    }

    @Test
    void testMultipleSkipReadPartsNoLength() throws IOException {
        try (Http1ClientResponse response = client.post("/")
                .queryParam("read", "false")
                .submit(WriteableMultiPart.builder()
                                .addPart(writeablePartNoLength("field_1", "a"))
                                .addPart(writeablePartNoLength("field_2", "b"))
                                .build())) {

            assertThat(response.status(), is(Status.NO_CONTENT_204));
            assertThat(NAMES, hasItems("field_1", "field_2"));
        }
    }

    @BeforeEach
    void clearNames() {
        NAMES.clear();
    }

    private static void multipartRoute(ServerRequest req, ServerResponse res) {
        boolean readIt = req.query().first("read").asBoolean().orElse(true);
        MultiPart multiPart = req.content().as(MultiPart.class);

        while (multiPart.hasNext()) {
            ReadablePart next = multiPart.next();
            NAMES.add(next.name());
            if (readIt) {
                next.consume();
            }
        }

        res.status(Status.NO_CONTENT_204).send();
    }

    private WriteablePart writeablePart(String partName, String content) throws IOException {
        return WriteablePart.builder(partName)
                .content(content.getBytes(StandardCharsets.UTF_8))
                .contentType(MediaTypes.MULTIPART_FORM_DATA)
                .build();
    }

    private WriteablePart writeablePartNoLength(String partName, String content) throws IOException {
        return WriteablePart.builder(partName)
                .contentType(MediaTypes.MULTIPART_FORM_DATA)
                .inputStream(() -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .build();
    }
}
