/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.nima.tests.integration.encoding.brotli;

import com.oracle.brotli.decoder.BrotliInputStream;
import com.oracle.brotli.encoder.BrotliOutputStream;
import io.helidon.common.http.Http;
import io.helidon.nima.testing.junit5.webserver.ServerTest;
import io.helidon.nima.testing.junit5.webserver.SetUpRoute;
import io.helidon.nima.webserver.http.HttpRouting;
import io.helidon.nima.webserver.http1.Http1Route;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
public class BrotliEncodingTest {

    private static final String ENTITY = "Some arbitrary text we want to try to compress";

    private final URI uri;
    private final HttpClient client;

    BrotliEncodingTest(URI uri) {
        this.uri = uri;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @SetUpRoute
    static void routing(HttpRouting.Builder builder) {
        builder.route(Http1Route.route(Http.Method.PUT,
                "/",
                (req, res) -> {
                    String entity = req.content().as(String.class);
                    if (!ENTITY.equals(entity)) {
                        res.status(Http.Status.INTERNAL_SERVER_ERROR_500).send("Wrong data");
                    } else {
                        res.send(entity);
                    }
                }));
    }

    @Test
    void testBrotliRequestAndResponse() throws IOException, InterruptedException {
        testIt("br");
    }

    @Test
    void testBrotliRequestAndResponseMultipleAcceptedEncodings() throws IOException, InterruptedException {
        testIt("br;q=0.9, gzip, *;q=0.1");
    }

    void testIt(String acceptEncodingValue) throws IOException, InterruptedException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        BrotliOutputStream bos = BrotliOutputStream.builder()
                .outputStream(baos)
                .build();
        bos.write(ENTITY.getBytes(StandardCharsets.UTF_8));
        bos.close();
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder()
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()))
                        .header("Accept-Encoding", acceptEncodingValue)
                        .headers("Content-Encoding", "br")
                        .uri(uri)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());

        byte[] bytes = response.body();
        String responseEntity;
        try {
            BrotliInputStream bis = BrotliInputStream.builder()
                    .inputStream(new ByteArrayInputStream(bytes))
                    .build();
            responseEntity = new String(bis.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            fail("Failed to read brotli response. Entity: " + new String(bytes), e);
            return;
        }

        Assertions.assertAll(
                () -> assertThat(response.statusCode(), is(200)),
                () -> assertThat(responseEntity, is(ENTITY)),
                () -> assertThat(response.headers().firstValue("Content-Encoding"), is(Optional.of("br")))
        );
    }
}
