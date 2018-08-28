/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.examples.quickstart.se;

import java.util.concurrent.TimeUnit;
import java.net.URL;
import java.net.HttpURLConnection;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainTest {

    private static WebServer webServer;

    @BeforeAll
    public static void startTheServer() throws Exception {
        webServer = Main.startServer();
        while (! webServer.isRunning()) {
            Thread.sleep(1 * 1000);
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
        JsonReader jsonReader = Json.createReader(conn.getInputStream());
        JsonObject jsonObject = jsonReader.readObject();
        Assertions.assertEquals("Hello World!", jsonObject.getString("message"),
                "default message");

        conn = getURLConnection("GET", "/greet/Joe");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response2");
        jsonReader = Json.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        Assertions.assertEquals("Hello Joe!", jsonObject.getString("message"),
                "hello Joe message");

        conn = getURLConnection("PUT", "/greet/greeting/Hola");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response3");
        conn = getURLConnection("GET", "/greet/Jose");
        Assertions.assertEquals(200, conn.getResponseCode(), "HTTP response4");
        jsonReader = Json.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        Assertions.assertEquals("Hola Jose!", jsonObject.getString("message"),
                "hola Jose message");
    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", "application/json");
        return conn;
    }
}
