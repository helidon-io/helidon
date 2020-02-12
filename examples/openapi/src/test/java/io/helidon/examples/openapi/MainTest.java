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

package io.helidon.examples.openapi;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonPointer;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonString;

import io.helidon.common.http.MediaType;
import io.helidon.examples.openapi.internal.SimpleAPIModelReader;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;

    private static final JsonReaderFactory JSON_RF = Json.createReaderFactory(Collections.emptyMap());

    private static final JsonBuilderFactory JSON_BF = Json.createBuilderFactory(Collections.emptyMap());

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

        conn = getURLConnection("GET","/greet");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response1");
        Assertions.assertEquals("Hello World!", GreetService.fromPayload(conn).getMessage(),
                "default message");

        conn = getURLConnection("GET", "/greet/Joe");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");
        Assertions.assertEquals("Hello Joe!", GreetService.fromPayload(conn).getMessage(),
                "hello Joe message");

        conn = getURLConnection("PUT", "/greet/greeting");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        GreetService.toPayload(conn, new GreetingMessage("Hola"));
        Assertions.assertEquals(204, conn.getResponseCode(), "HTTP response3");

        conn = getURLConnection("GET", "/greet/Jose");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response4");
        Assertions.assertEquals("Hola Jose!", GreetService.fromPayload(conn).getMessage(),
                "hola Jose message");

        conn = getURLConnection("GET", "/health");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");

        conn = getURLConnection("GET", "/metrics");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");
    }

    @Test
    public void testOpenAPI() throws Exception {
        HttpURLConnection conn;

        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", MediaType.APPLICATION_JSON.toString());
        /*
         * If you change the OpenAPI endpoing path in application.yaml, then
         * change the following path also.
         */
        conn = getURLConnection("GET", "/openapi", headers);
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response");
        JsonReader jsonReader = JSON_RF.createReader(conn.getInputStream());
        JsonObject paths = jsonReader.readObject().getJsonObject("paths");

        JsonPointer jp = Json.createPointer("/" + escape("/greet/greeting") + "/put/summary");
        JsonString js = JsonString.class.cast(jp.getValue(paths));
        Assertions.assertEquals("Set the greeting prefix", js.getString(), "/greet/greeting.put.summary not as expected");

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.MODEL_READER_PATH)
                + "/get/summary");
        js = JsonString.class.cast(jp.getValue(paths));
        Assertions.assertEquals(SimpleAPIModelReader.SUMMARY, js.getString(),
                "summary added by model reader does not match");

        jp = Json.createPointer("/" + escape(SimpleAPIModelReader.DOOMED_PATH));
        Assertions.assertFalse(jp.containsValue(paths), "/test/doomed should not appear but does");
    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        return getURLConnection(method, path, Collections.emptyMap());
    }

    private HttpURLConnection getURLConnection(String method, String path, Map<String, String> headers) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        for (Map.Entry<String,String> header : headers.entrySet()) {
            conn.setRequestProperty(header.getKey(), header.getValue());
        }
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    private static String escape(String path) {
        return path.replace("/", "~1");
    }

}
