/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.helidon.common.http.MediaType;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class TestNettyRejectRequest {

    private static WebServer server;

    @BeforeAll
    public static void createAndStartServer() throws Exception {
        server = WebServer.builder()
                .host("localhost")
                .routing(Routing.builder()
                        .get((req, res) -> {
                            res.send("test");
                        })
                        .build())
                .port(0)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void stopServer() throws Exception {
        server.shutdown().toCompletableFuture().get();
    }

    @Test
    public void testBadHeader() throws Exception {
        // Cannot use WebClient or HttpURLConnection for this test because they use Netty's DefaultHttpHeaders
        // which prevents bad headers from being sent to the server.

        Socket socket = new Socket("localhost", server.port());
        BufferedWriter w = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), Charset.defaultCharset()));
        BufferedReader r = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        w.write("GET /any HTTP/1.1\r\n");
        w.write("Accept: text/plain\r\n");
        w.write("Bad=Header: anything\r\n");
        w.write("\r\n");
        w.flush();

        StringBuilder sb = new StringBuilder();
        String line;
        int status = -1;
        Map<String, String> headers = new HashMap<>();
        Pattern headerPattern = Pattern.compile("([^:]+):\\s(.+)");

        while ((line = r.readLine()) != null && !line.isBlank()) {
            sb.append(line)
                    .append(System.lineSeparator());
            if ("HTTP".equalsIgnoreCase(line.substring(0, "HTTP".length()))) {
                int statusStart = line.indexOf(" ");
                int statusEnd = line.indexOf(" ", statusStart+1);
                status = Integer.parseInt(line, statusStart+1, statusEnd, 10);
            } else {
                Matcher m = headerPattern.matcher(line);
                if (m.matches()) {
                    headers.put(m.group(1), m.group(2));
                }
            }
        }
        if (headers.get("content-length") != null) {
            int contentLength = Integer.parseInt(headers.get("content-length"));
            if (contentLength > 0) {
                char[] content = new char[contentLength];
                int charsRead = r.read(content);
                sb.append(content, 0, charsRead)
                        .append(System.lineSeparator());
            }
        }
        r.close();

        assertThat(status, is(400));
        assertThat(sb.toString(), containsString("prohibited characters"));
    }

    private static HttpURLConnection getURLConnection(
            int port,
            String method,
            String path,
            MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (mediaType != null) {
            conn.setRequestProperty("Accept", mediaType.toString());
        }
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    public static String stringFromResponse(HttpURLConnection cnx, MediaType mediaType) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(
                cnx.getInputStream(), mediaType.charset().get())) {
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
