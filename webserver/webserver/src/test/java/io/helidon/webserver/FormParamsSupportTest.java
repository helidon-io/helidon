/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
package io.helidon.webserver;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.helidon.common.http.FormParams;
import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.containsString;




public class FormParamsSupportTest {

    private static WebServer testServer;

    @BeforeAll
    public static void startup() throws InterruptedException, ExecutionException, TimeoutException {
        testServer = WebServer.create(ServerConfiguration.builder()
                        .port(0)
                        .build(),
                    Routing.builder()
                        .register(FormParamsSupport.create())
                        .put("/params", (req, resp) -> {
                            req.content().as(FormParams.class).thenAccept(fp ->
                                    resp.send(fp.toMap().toString()));
                        })
                        .build())
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void shutdown() {
        testServer.shutdown();
    }

    @Test
    public void urlEncodedTest() throws Exception {
        HttpURLConnection cnx = getURLConnection(testServer.port(),
                "PUT", "/params", MediaType.APPLICATION_FORM_URLENCODED);
        stringToRequest(cnx, "key1=val+1&key2=val2_1&key2=val2_2");
        String response = stringFromResponse(cnx, MediaType.TEXT_PLAIN);

        assertThat(response, containsString("key1=[val 1]"));
        assertThat(response, containsString("key2=[val2_1, val2_2]"));
    }

    @Test
    public void plainTextTest() throws Exception{
        HttpURLConnection cnx = getURLConnection(testServer.port(),
                "PUT", "/params", MediaType.TEXT_PLAIN);
        stringToRequest(cnx, "key1=val 1\nkey2=val2_1\nkey2=val2_2");
        String response = stringFromResponse(cnx, MediaType.TEXT_PLAIN);

        assertThat(response, containsString("key1=[val 1]"));
        assertThat(response, containsString("key2=[val2_1, val2_2]"));
    }

    private static HttpURLConnection getURLConnection(
            int port,
            String method,
            String path,
            MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setReadTimeout(20000 /* ms */);
        if (mediaType != null) {
            conn.setRequestProperty("Content-Type", mediaType.toString());
        }
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    private static void stringToRequest(HttpURLConnection cnx, String payload) throws IOException {
        cnx.setDoOutput(true);
        OutputStreamWriter osw = new OutputStreamWriter(cnx.getOutputStream());
        osw.write(payload);
        osw.flush();
        osw.close();
    }

    private static String stringFromResponse(HttpURLConnection cnx, MediaType mediaType) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(
                cnx.getInputStream(), mediaType.charset().orElse(StandardCharsets.UTF_8.name()))) {
            StringBuilder sb = new StringBuilder();
            CharBuffer cb = CharBuffer.allocate(1024);
            while (isr.read(cb) != -1) {
                cb.flip();
                sb.append(cb);
            }
            return sb.toString();
        }
    }
}
