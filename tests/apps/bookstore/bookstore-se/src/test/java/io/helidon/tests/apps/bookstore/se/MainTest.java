/*
 * Copyright (c) 2018, 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.tests.apps.bookstore.se;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("Failed to start webserver");
            }
        }
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
    public void testHelloWorld() throws Exception {
        HttpURLConnection conn;
        String json = getBookAsJson();

        conn = getURLConnection("GET","/books");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response1");

        conn = getURLConnection("POST","/books");
        writeJsonContent(conn, json);
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response POST");

        conn = getURLConnection("GET","/books/123456");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response GET good ISBN");
        JsonReader jsonReader = Json.createReader(conn.getInputStream());
        JsonObject jsonObject = jsonReader.readObject();
        Assertions.assertEquals("123456", jsonObject.getString("isbn"),
                "Checking if correct ISBN");

        conn = getURLConnection("GET","/books/0000");
        Assertions.assertEquals(404, conn.getResponseCode(), "HTTP response GET bad ISBN");

        conn = getURLConnection("GET","/books");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response list books");

        conn = getURLConnection("DELETE","/books/123456");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response delete book");

    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    private String getBookAsJson() {
        InputStream is = getClass().getClassLoader().getResourceAsStream("book.json");
        if (is != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        return null;
    }

    private int writeJsonContent(HttpURLConnection conn, String json) throws IOException {
        int jsonLength = json.getBytes().length;

        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Content-Length", Integer.toString(jsonLength));
        conn.setFixedLengthStreamingMode(jsonLength);
        OutputStream outputStream = conn.getOutputStream();
        outputStream.write(json.getBytes());
        outputStream.close();

        return jsonLength;
    }

}
