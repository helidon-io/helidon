/*
 * Copyright (c) 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.util.concurrent.TimeUnit;

import javax.json.JsonObject;
import javax.json.JsonString;

import io.helidon.common.http.MediaType;
import io.helidon.media.jsonp.JsonpSupport;
import io.helidon.media.multipart.FileFormParams;
import io.helidon.media.multipart.MultiPartSupport;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientResponse;
import io.helidon.webserver.WebServer;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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
public class FileServiceTest {

    private static WebServer webServer;
    private static WebClient webClient;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer().await();

        webClient = WebClient.builder()
                             .baseUri("http://localhost:8080/api")
                             .addMediaSupport(MultiPartSupport.create())
                             .addMediaSupport(JsonpSupport.create())
                             .build();
    }

    @AfterAll
    public static void stopServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    @Order(1)
    public void testUpload() throws IOException {
        Path file = Files.write( Files.createTempFile(null, null), "bar\n".getBytes(StandardCharsets.UTF_8));
        WebClientResponse response = webClient
                .post()
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .submit(FileFormParams.builder()
                                      .addFile("file[]", "foo.txt", file)
                                      .build())
                .await();
        assertThat(response.status().code(), is(301));
    }

    @Test
    @Order(2)
    public void testStreamUpload() throws IOException {
        Path file = Files.write( Files.createTempFile(null, null), "stream bar\n".getBytes(StandardCharsets.UTF_8));
        Path file2 = Files.write( Files.createTempFile(null, null), "stream foo\n".getBytes(StandardCharsets.UTF_8));
        WebClientResponse response = webClient
                .post()
                .queryParam("stream", "true")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .submit(FileFormParams.builder()
                                      .addFile("file[]", "streamed-foo.txt", file)
                                      .addFile("otherPart", "streamed-foo2.txt", file2)
                                      .build())
                .await(2, TimeUnit.SECONDS);
        assertThat(response.status().code(), is(301));
    }

    @Test
    @Order(3)
    public void testList() {
        WebClientResponse response = webClient
                .get()
                .contentType(MediaType.APPLICATION_JSON)
                .request()
                .await();
        assertThat(response.status().code(), Matchers.is(200));
        JsonObject json = response.content().as(JsonObject.class).await();
        assertThat(json, Matchers.is(notNullValue()));
        List<String> files = json.getJsonArray("files").getValuesAs(v -> ((JsonString) v).getString());
        assertThat(files, hasItem("foo.txt"));
    }

    @Test
    @Order(4)
    public void testDownload() {
        WebClientResponse response = webClient
                .get()
                .path("foo.txt")
                .accept(MediaType.APPLICATION_OCTET_STREAM)
                .request()
                .await();
        assertThat(response.status().code(), is(200));
        assertThat(response.headers().first("Content-Disposition").orElse(null),
                containsString("filename=\"foo.txt\""));
        byte[] bytes = response.content().as(byte[].class).await();
        assertThat(new String(bytes, StandardCharsets.UTF_8), Matchers.is("bar\n"));
    }
}
