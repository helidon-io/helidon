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
package io.helidon.tests.integration.yamlparsing;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.MediaType;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class MainTest {

    private static final JsonBuilderFactory JSON_BUILDER = Json.createBuilderFactory(Collections.emptyMap());
    private static final JsonObject TEST_JSON_OBJECT = JSON_BUILDER.createObjectBuilder()
            .add("greeting", "Hola")
            .build();

    private static WebServer webServer;

    @BeforeAll
    static void startTheServer() throws IOException, InterruptedException {
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
    static void stopServer() throws ExecutionException, InterruptedException, TimeoutException {
        if (webServer != null) {
            webServer.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }


    @Test
    void testOpenApi() throws Exception {
        HttpURLConnection cnx = getURLConnection("GET", "/openapi", MediaType.APPLICATION_OPENAPI_YAML);
        String openApiText = TestUtil.stringFromResponse(cnx);
        assertThat("Response code", cnx.getResponseCode(), is(200));
        assertThat("OpenAPI response", openApiText, containsString("title: Swagger Petstore"));
    }

    @Test
    void testGreetings() throws Exception {
        JsonObject jsonObject;

        HttpURLConnection cnx = getURLConnection("GET", "/greet/Joe");

        jsonObject = TestUtil.jsonFromResponse(cnx).asJsonObject();
        assertThat(jsonObject.getString("message"), is("Hello Joe!"));

        cnx = getURLConnection("PUT", "/greet/greeting");
        cnx.setRequestProperty("Content-Type", MediaType.APPLICATION_JSON.toString());
        cnx.setDoOutput(true);
        OutputStream os = cnx.getOutputStream();
        os.write("{\"greeting\" : \"Hola\"}".getBytes());
        os.close();

        assertThat(cnx.getResponseCode(), is(204));

        cnx = getURLConnection("GET", "/greet/Jose");
        jsonObject = TestUtil.jsonFromResponse(cnx).asJsonObject();
        assertThat(jsonObject.getString("message"), is("Hola Jose!"));
    }

    @Test
    void verifySnakeYamlVersion() {
        String selectedSnakeYamlVersion = System.getProperty("selected-snakeyaml-version");
        assertThat("Spec of selected SnakeYAML version", selectedSnakeYamlVersion, is(notNullValue()));

        String classpath = System.getProperty("java.class.path");
        assertThat("SnakeYAML version in classpath",
                   classpath,
                   containsString("snakeyaml-" + selectedSnakeYamlVersion));
    }

    private HttpURLConnection getURLConnection(String method, String path) throws Exception {
        return getURLConnection(method, path, MediaType.APPLICATION_JSON);
    }

    private HttpURLConnection getURLConnection(String method, String path, MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Accept", mediaType.toString());
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

}