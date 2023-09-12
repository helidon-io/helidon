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

package io.helidon.http.tests.integration.multipart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HttpMediaType;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.http.media.multipart.MultiPart;
import io.helidon.http.media.multipart.ReadablePart;
import io.helidon.http.media.multipart.WriteableMultiPart;
import io.helidon.http.media.multipart.WriteablePart;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientResponse;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ServerTest
class MultipartTest {
    private static final String FIRST_PART_NAME = "first";
    private static final String SECOND_PART_NAME = "second";
    private static final String FIRST_PART_CONTENT = "\r\nfirst multipart\n";
    private static final String SECOND_PART_CONTENT = "\nsecond multipart\r\n";
    private static final String SECOND_PART_FILENAME = "second.txt";

    private final Http1Client client;

    MultipartTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRules rules) {
        rules.get("/multipart", (req, res) -> {
                    WriteableMultiPart multiPart = WriteableMultiPart.builder()
                            .addPart(WriteablePart.builder(FIRST_PART_NAME)
                                             .content(FIRST_PART_CONTENT))
                            .addPart(WriteablePart.builder(SECOND_PART_NAME)
                                             .fileName(SECOND_PART_FILENAME)
                                             .contentType(MediaTypes.APPLICATION_OCTET_STREAM)
                                             .inputStream(() -> new ByteArrayInputStream(SECOND_PART_CONTENT.getBytes(StandardCharsets.UTF_8))))
                            .build();
                    res.send(multiPart);
                })
                .post("/multipart", (req, res) -> {
                    MultiPart multiPart = req.content().as(MultiPart.class);
                    List<String> parts = new LinkedList<>();
                    while (multiPart.hasNext()) {
                        ReadablePart next = multiPart.next();
                        parts.add(next.name() + ":" + next.as(String.class));
                    }
                    res.send(String.join(",", parts));
                });
    }

    @Test
    void testWriteMultipart() {
        WriteableMultiPart multiPart = WriteableMultiPart.builder()
                .addPart(WriteablePart.builder(FIRST_PART_NAME)
                                 .content(FIRST_PART_CONTENT))
                .addPart(WriteablePart.builder(SECOND_PART_NAME)
                                 .fileName(SECOND_PART_FILENAME)
                                 .contentType(MediaTypes.APPLICATION_OCTET_STREAM)
                                 .inputStream(() -> new ByteArrayInputStream(SECOND_PART_CONTENT.getBytes(StandardCharsets.UTF_8))))
                .build();

        try (Http1ClientResponse response = client.method(Method.POST)
                .path("/multipart")
                .submit(multiPart)) {

            assertThat(response.status(), is(Status.OK_200));
            assertThat(response.as(String.class), is(FIRST_PART_NAME + ":" + FIRST_PART_CONTENT + ","
                                                             + SECOND_PART_NAME + ":" + SECOND_PART_CONTENT));
        }
    }

    @Test
    void testReadMultipart() throws IOException {
        try (Http1ClientResponse response = client.get("/multipart")
                .request()) {
            assertThat(response.status(), is(Status.OK_200));
            MultiPart mp = response.as(MultiPart.class);

            ReadablePart part = mp.next();
            assertThat(part.name(), is(FIRST_PART_NAME));
            assertThat(part.as(String.class), is(FIRST_PART_CONTENT));

            part = mp.next();
            assertThat(part.name(), is(SECOND_PART_NAME));
            assertThat(part.fileName(), optionalValue(is(SECOND_PART_FILENAME)));
            assertThat(part.contentType(), is(HttpMediaType.create(MediaTypes.APPLICATION_OCTET_STREAM)));
            try (InputStream inputStream = part.inputStream();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                inputStream.transferTo(out);
                assertThat(out.toString(StandardCharsets.UTF_8), is(SECOND_PART_CONTENT));
            }
        }
    }
}
