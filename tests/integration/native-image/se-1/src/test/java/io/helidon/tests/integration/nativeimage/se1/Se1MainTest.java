/*
 * Copyright (c) 2019, 2022 Oracle and/or its affiliates.
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

package io.helidon.tests.integration.nativeimage.se1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonReaderFactory;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for {@link Se1Main}.
 */
class Se1MainTest {
    private static WebServer webServer;
    private static final JsonReaderFactory JSON = Json.createReaderFactory(Collections.emptyMap());

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Se1Main.startServer();

        long timeout = 2000; // 2 seconds should be enough to start the server
        long now = System.currentTimeMillis();

        while (!webServer.isRunning()) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                fail("Failed to start webserver");
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

        conn = getURLConnection("GET","/greet");
        assertThat("HTTP response1", conn.getResponseCode(), is(200));
        JsonReader jsonReader = JSON.createReader(conn.getInputStream());
        JsonObject jsonObject = jsonReader.readObject();
        assertThat("default message", jsonObject.getString("message"),
                is("Hello World!"));

        conn = getURLConnection("GET", "/greet/Joe");
        assertThat("HTTP response2 - not authenticated", conn.getResponseCode(), is(200));
        jsonReader = JSON.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        assertThat("hello Joe message", jsonObject.getString("message"),
                is("Hello Joe!"));

        conn = getURLConnection("PUT", "/greet/greeting");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write("{\"greeting\" : \"Hola\"}".getBytes());
        os.close();
        assertThat("HTTP response3", conn.getResponseCode(), is(204));

        conn = getURLConnection("GET", "/greet/Jose");
        assertThat("HTTP response4", conn.getResponseCode(), is(200));
        jsonReader = JSON.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        assertThat("hola Jose message", jsonObject.getString("message"),
                is("Hola Jose!"));

        conn = getURLConnection("GET", "/health");
        assertThat("HTTP response2", conn.getResponseCode(), is(200));

        conn = getURLConnection("GET", "/metrics");
        assertThat("HTTP response2", conn.getResponseCode(), is(200));
    }

    @Test
    void testEnumMapping() throws Exception {
        HttpURLConnection conn = getURLConnection("GET", "/color");
        int status = conn.getResponseCode();
        ColorService.Color tint;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String colorName = reader.readLine(); // Makes sure the color name was sent.
            tint = ColorService.Color.valueOf(colorName); // Makes sure the color name maps to a Color.
        }
        assertThat("/color GET status", status, is(200));
        assertThat("reported tint", tint, is(ColorService.Color.RED)); // Makes sure the mapped color is RED.
    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }
}
