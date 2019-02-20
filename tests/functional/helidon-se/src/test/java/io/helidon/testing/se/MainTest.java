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

package io.helidon.testing.se;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

public class MainTest {

    private static WebServer webServer;

    /*
     * WebServer is started by each test so they can control configuration
     */
    void startTheServer() throws Exception {
        if (webServer !=  null) {
            Assertions.fail("Can't start the WebServer, it is already running");
        }
        webServer = Main.startServer();
        waitForServerUp(true);
    }

    @AfterEach
    void stopTheServer() throws Exception {
        if (webServer != null) {
            webServer.shutdown()
                     .toCompletableFuture()
                     .get(10, TimeUnit.SECONDS);
        }
        waitForServerUp(false);
        webServer=null;
    }

    /**
     * Run some basic CRUD operations on the server. The server supports
     * running with any of our three JSON libraries: jsonp, jsonb, jackson.
     * So we set a system property to select the library to use before starting
     * the server
     *
     * @param jsonLibrary "jsonp", "jsonb" or "jackson"
     * @throws Exception
     */
    private void runJsonFunctionalTest(String jsonLibrary) throws Exception {
        HttpURLConnection conn;
        String json = getBookAsJson();

        System.setProperty("app.json-library", jsonLibrary);
        startTheServer();

        conn = getURLConnection("GET","/books");
        assertThat("HTTP response GET books", conn.getResponseCode(), is(200));

        conn = getURLConnection("POST","/books");
        writeJsonContent(conn, json);
        assertThat("HTTP response POST", conn.getResponseCode(), is(200));

        conn = getURLConnection("GET","/books/123456");
        assertThat("HTTP response GET good ISBN", conn.getResponseCode(), is(200));
        JsonReader jsonReader = Json.createReader(conn.getInputStream());
        JsonObject jsonObject = jsonReader.readObject();
        assertThat("Checking if correct ISBN", jsonObject.getString("isbn"), is("123456"));

        conn = getURLConnection("GET","/books/0000");
        assertThat("HTTP response GET bad ISBN", conn.getResponseCode(), is(404));

        conn = getURLConnection("GET","/books");
        assertThat("HTTP response list books", conn.getResponseCode(), is(200));

        conn = getURLConnection("DELETE","/books/123456");
        assertThat("HTTP response delete book", conn.getResponseCode(), is(200));
    }

    @Test
    public void basicTestJsonP() throws Exception {
        runJsonFunctionalTest("jsonp");
    }

    @Test
    public void basicTestJsonB() throws Exception {
        runJsonFunctionalTest("jsonb");
    }

    @Test
    public void basicTestJackson() throws Exception {
        runJsonFunctionalTest("jackson");
    }

    @Test
    public void metricsAndHealth() throws Exception {
        startTheServer();
        HttpURLConnection conn;

        // Get Prometheus style metrics
        conn = getURLConnection("GET","/metrics");
        conn.setRequestProperty("Accept", "*/*");
        assertThat("Checking Prometheus Metrics response\"", conn.getResponseCode(), is(200));
        String s = readAllAsString(conn.getInputStream());

        // Make sure we got prometheus metrics
        assertThat("Making sure we got Prometheus format", s.startsWith("\"# TYPE"));

        // Get JSON encoded metrics
        conn = getURLConnection("GET","/metrics");
        assertThat("Checking JSON Metrics response\"", conn.getResponseCode(), is(200));

        // Makes sure we got JSON metrics
        JsonReader jsonReader = Json.createReader(conn.getInputStream());
        JsonObject jsonObject = jsonReader.readObject();
        assertThat("Checking request count",
                jsonObject.getJsonObject("vendor").getInt("requests.count") > 0);

        // Get JSON encoded metrics/base
        conn = getURLConnection("GET","/metrics/base");
        assertThat("Checking JSON Base Metrics response", conn.getResponseCode(), is(200));

        // Makes sure we got JSON metrics
        jsonReader = Json.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        assertThat("Checking thread count", jsonObject.getInt("thread.count") > 0);

        // Get JSON encoded health check
        conn = getURLConnection("GET","/health");
        assertThat("Checking health response", conn.getResponseCode(), is(200));

        jsonReader = Json.createReader(conn.getInputStream());
        jsonObject = jsonReader.readObject();
        assertThat("Checking health outcome", jsonObject.getString("outcome"), is("UP"));
    }

    @Test
    public void routing() throws Exception {
        startTheServer();
        HttpURLConnection conn;
        conn = getURLConnection("GET","/boo%6bs");
        assertThat("Checking encode URL response", conn.getResponseCode(), is(200));

        // TODO: add service to application that uses more ambiguous/complex
        // routing definitions
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

    private String readAllAsString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        return reader.lines().collect(Collectors.joining(System.lineSeparator()));
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

    private static void waitForServerUp(boolean up) throws Exception {
        long timeout = 4000; // Wait at most 4 seconds
        long now = System.currentTimeMillis();

        while ( (up && !webServer.isRunning()) || (!up && webServer.isRunning())) {
            Thread.sleep(100);
            if ((System.currentTimeMillis() - now) > timeout) {
                Assertions.fail("WebServer did not " + (up ? "start up" : "shut down"));
            }
        }
    }

}
