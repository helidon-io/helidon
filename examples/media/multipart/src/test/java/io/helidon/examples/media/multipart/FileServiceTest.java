/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.examples.media.multipart;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.helidon.common.http.Http;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.nima.http.media.multipart.WriteableMultiPart;
import io.helidon.nima.http.media.multipart.WriteablePart;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientResponse;
import io.helidon.nima.webserver.http.HttpRouting;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Tests {@link FileService}.
 */
@TestMethodOrder(OrderAnnotation.class)
@ServerTest
public class FileServiceTest {
    private final Http1Client client;

    FileServiceTest(Http1Client client) {
        this.client = client;
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        Main.routing(builder);
    }

    @Test
    @Order(1)
    public void testUpload() throws IOException {
        Path file = Files.writeString(Files.createTempFile(null, null), "bar\n");
        try (Http1ClientResponse response = client.post("/api")
                                                  .followRedirects(false)
                                                  .submit(WriteableMultiPart.builder()
                                                                            .addPart(writeablePart("file[]", "foo.txt", file))
                                                                            .build())) {
            assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
        }
    }

    @Test
    @Order(2)
    public void testStreamUpload() throws IOException {
        Path file = Files.writeString(Files.createTempFile(null, null), "stream bar\n");
        Path file2 = Files.writeString(Files.createTempFile(null, null), "stream foo\n");
        try (Http1ClientResponse response = client.post("/api")
                                                  .queryParam("stream", "true")
                                                  .followRedirects(false)
                                                  .submit(WriteableMultiPart
                                                          .builder()
                                                          .addPart(writeablePart("file[]", "streamed-foo.txt", file))
                                                          .addPart(writeablePart("otherPart", "streamed-foo2.txt", file2))
                                                          .build())) {
            assertThat(response.status(), is(Http.Status.MOVED_PERMANENTLY_301));
        }
    }

    @Test
    @Order(3)
    public void testList() {
        try (Http1ClientResponse response = client.get("/api").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            JsonObject json = response.as(JsonObject.class);
            assertThat(json, Matchers.is(notNullValue()));
            List<String> files = json.getJsonArray("files").getValuesAs(v -> ((JsonString) v).getString());
            assertThat(files, hasItem("foo.txt"));
        }
    }

    @Test
    @Order(4)
    public void testDownload() {
        try (Http1ClientResponse response = client.get("/api").path("foo.txt").request()) {
            assertThat(response.status(), is(Http.Status.OK_200));
            assertThat(response.headers().first(Http.HeaderNames.CONTENT_DISPOSITION).orElse(null),
                    containsString("filename=\"foo.txt\""));
            byte[] bytes = response.as(byte[].class);
            assertThat(new String(bytes, StandardCharsets.UTF_8), Matchers.is("bar\n"));
        }
    }

    private WriteablePart writeablePart(String partName, String fileName, Path filePath) throws IOException {
        return WriteablePart.builder(partName)
                            .fileName(fileName)
                            .content(Files.readAllBytes(filePath))
                            .contentType(MediaTypes.MULTIPART_FORM_DATA)
                            .build();
    }
}
