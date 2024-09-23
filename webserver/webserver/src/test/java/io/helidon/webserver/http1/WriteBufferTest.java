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

package io.helidon.webserver.http1;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http1.Http1ServerResponse.BlockingOutputStream;
import io.helidon.webserver.http1.Http1ServerResponse.ClosingBufferedOutputStream;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.helidon.webserver.http.ServerResponse;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class WriteBufferTest {

    /**
     * Test that a simple response can be sent using the {@link ServerResponse#outputStream()} using the default
     * (non-zero) write buffer.
     */
    @Test
    void defaultWriteBufferTest() throws Exception {
        String path = "/test";
        String response = "Hello World!";
        Handler handler = (req, res) -> {
            try(OutputStream out = res.outputStream()) {
                assertThat(out, instanceOf(ClosingBufferedOutputStream.class));
                out.write(response.getBytes(StandardCharsets.UTF_8));
            }
        };
        WebServer server = WebServer.builder().port(0).routing(it -> it.get(path, handler)).build().start();
        try {
            URL url = new URI("http://localhost:" + server.port() + path).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            assertThat(conn.getResponseCode(), is(200));
            String received = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(received, is(response));
        } finally {
            server.stop();
        }
    }

    /**
     * Test that a simple response can be sent using the {@link ServerResponse#outputStream()} using no write buffer
     * (i.e. the write buffer size was set to {@code 0}).
     */
    @Test
    void noWriteBufferTest() throws Exception {
        String path = "/test";
        String response = "Hello World!";
        Handler handler = (req, res) -> {
            try(OutputStream out = res.outputStream()) {
                assertThat(out, instanceOf(BlockingOutputStream.class));
                out.write(response.getBytes(StandardCharsets.UTF_8));
            }
        };
        WebServer server = WebServer.builder().port(0).writeBufferSize(0)
                .routing(it -> it.get(path, handler)).build().start();
        try {
            URL url = new URI("http://localhost:" + server.port() + path).toURL();
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
            conn.setRequestMethod("GET");
            assertThat(conn.getResponseCode(), is(200));
            String received = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(received, is(response));
        } finally {
            server.stop();
        }
    }

}
