/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import io.helidon.http.Header;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;
import io.helidon.http.Method;
import io.helidon.http.Status;
import io.helidon.webclient.api.WebClient;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.ServerTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.fail;

@ServerTest
class KeepAliveTest {

    private static final Header TEST_TRAILER = HeaderValues.createCached("test-trailer", "test");
    private final WebClient client;

    @SetUpRoute
    static void route(HttpRouting.Builder router) {
        router.any("/entity-port", (req, res) -> res
                .send(String.valueOf(req.remotePeer().port())));
        router.any("/entity-chunked", (req, res) -> {
            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            try (var os = res.outputStream()) {
                os.write(String.valueOf(req.remotePeer().port()).getBytes(StandardCharsets.UTF_8));
            }
        });
        router.any("/mid-read", (req, res) -> {
            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            res.header(HeaderNames.TRAILER, TEST_TRAILER.name());
            res.trailers().add(TEST_TRAILER);
            res.status(req.remotePeer().port());
            res.send("Unfinished busine...ss!");
        });
        router.any("/mid-read-chunked", (req, res) -> {
            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            res.status(req.remotePeer().port());
            try (var os = res.outputStream()){
                os.write("Unfinished busine...".getBytes(StandardCharsets.UTF_8));
                os.write("ss!".getBytes(StandardCharsets.UTF_8));
            }
        });
        router.any("/mid-read-chunked-trailers", (req, res) -> {
            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            res.header(HeaderNames.TRAILER, TEST_TRAILER.name());
            res.status(req.remotePeer().port());
            res.trailers().add(TEST_TRAILER);
            try (var os = res.outputStream()){
                os.write("Unfinished busine...".getBytes(StandardCharsets.UTF_8));
                os.write("ss!".getBytes(StandardCharsets.UTF_8));
            }
        });
        router.any("/empty-chunked", (req, res) -> {
            res.header(HeaderValues.TRANSFER_ENCODING_CHUNKED);
            res.status(req.remotePeer().port());
            res.outputStream().close();
        });
        router.any("/empty", (req, res) -> res.send("GET"));
        router.any("/timeout", (req, res) -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                //ignored
            }
            res.send("GET");
        });
    }

    KeepAliveTest(WebClient client) {
        this.client = client;
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "POST", "DELETE"})
    void noEntity(String method) {
        var m = Method.create(method);
        Status port;
        try(var res = client.method(m).path("/empty").request()){
            port = res.status();
        }

        try(var res2 = client.method(m).path("/empty").request()){
            assertThat(res2.status(), is(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "POST", "DELETE"})
    void noReqEntity(String method) {
        var m = Method.create(method);
        String port;
        try(var res = client.method(m).path("/entity-port").request()){
            port = res.as(String.class);
        }
        try(var res2 = client.method(m).path("/entity-port").request()){
            assertThat(res2.as(String.class), is(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "PUT", "POST", "DELETE"})
    void noReqEntityChunked(String method) {
        var m = Method.create(method);
        String port;
        try(var res = client.method(m).path("/entity-chunked").request()){
            port = res.as(String.class);
        }
        try(var res2 = client.method(m).path("/entity-chunked").request()){
            assertThat(res2.as(String.class), is(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "POST"})
    void noResEntity(String method) {
        var m = Method.create(method);
        Status port;
        try(var res = client.method(m).path("/empty").submit("Hello")){
            port = res.status();
        }
        try(var res2 = client.method(m).path("/empty").submit("Hello")){
            assertThat(res2.status(), is(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "POST"})
    void noResEntityChunked(String method) {
        var m = Method.create(method);
        Status port;
        try(var res = client.method(m).path("/empty-chunked").submit("Hello")){
            port = res.status();
        }
        try(var res2 = client.method(m).path("/empty-chunked").submit("Hello")){
            assertThat(res2.status(), is(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUT", "POST"})
    void withReqResEntity(String method) {
        var m = Method.create(method);
        String port;
        try(var res = client.method(m).path("/entity-port").submit("Hello")){
            port = res.as(String.class);
        }
        try(var res2 = client.method(m).path("/entity-port").submit("Hello")){
            assertThat(res2.as(String.class), is(port));
        }
    }

    @Test
    void closedMidRead() throws IOException {
        int length = "Unfinished busine".getBytes(StandardCharsets.UTF_8).length;

        var m = Method.GET;
        Status port;
        try(var res = client.method(m).path("/mid-read").request()){
            var part = new String(res.inputStream().readNBytes(length));
            port = res.status();
            assertThat(part, is("Unfinished busine"));
        }

        try(var res = client.method(m).path("/mid-read").request()){
            var part = res.as(String.class);
            assertThat(part, is("Unfinished busine...ss!"));
            assertThat(res.status(), not(port));
        }
    }

    @Test
    void closedMidReadWithTrailers() throws IOException {
        int length = "Unfinished busine".getBytes(StandardCharsets.UTF_8).length;

        var m = Method.GET;
        Status port;
        try(var res = client.method(m).path("/mid-read").request()){
            var part = new String(res.inputStream().readNBytes(length));
            port = res.status();
            assertThat(part, is("Unfinished busine"));
        }

        try(var res = client.method(m).path("/mid-read").request()){
            var part = res.as(String.class);
            assertThat(res.trailers().get(TEST_TRAILER.headerName()), is(TEST_TRAILER));
            assertThat(part, is("Unfinished busine...ss!"));
            assertThat(res.status(), not(port));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET"})
    void chunkedClosedMidRead(String method) throws IOException {
        int length = "Unfinished busine...".getBytes(StandardCharsets.UTF_8).length;

        var m = Method.create(method);
        try(var res = client.method(m).path("/mid-read-chunked").request()){
            res.inputStream().readNBytes(length);
        }
        try(var res = client.method(m).path("/mid-read-chunked").request()){
            var part = new String(res.inputStream().readAllBytes());
            assertThat(part, is("Unfinished busine...ss!"));
        }
    }

    @Test
    void chunkedClosedMidReadWithTrailers() throws IOException {
        int length = "Unfinished busine".getBytes(StandardCharsets.UTF_8).length;

        var m = Method.GET;
        Status port;
        try(var res = client.method(m).path("/mid-read-chunked-trailers").request()){
            var part = new String(res.inputStream().readNBytes(length));
            port = res.status();
            assertThat(part, is("Unfinished busine"));
        }

        try(var res = client.method(m).path("/mid-read-chunked-trailers").request()){
            var part = new String(res.inputStream().readAllBytes());
            assertThat(res.trailers().get(TEST_TRAILER.headerName()), is(TEST_TRAILER));
            assertThat(part, is("Unfinished busine...ss!"));
            assertThat(res.status(), not(port));
        }
    }

    /**
     * We need to avoid reusing connection with different read timeout.
     */
    @Test
    void timeoutOnReusedConnection() {
        var m = Method.GET;
        try(var res = client.method(m).path("/empty").request()){
            res.as(String.class);
        }

        WebClient c = WebClient.builder()
                .readTimeout(Duration.ofMillis(200))
                .baseUri("http://localhost:" + client.prototype().baseUri().get().port())
                .build();

        try(var res = c.method(m).path("/timeout").request()){
            fail("Timeout expected!");
        } catch (UncheckedIOException e){
            assertThat(e.getCause(), instanceOf(SocketTimeoutException.class));
        }
    }
}
