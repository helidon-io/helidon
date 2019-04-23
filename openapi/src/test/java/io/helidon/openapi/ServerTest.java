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
 *
 */
package io.helidon.openapi;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Map;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;

/**
 * Starts a server with the default OpenAPI endpoint to test a static OpenAPI
 * document file in various ways.
 */
public class ServerTest {

    private static WebServer webServer;

    private static final String QUICKSTART_PATH = "/openapi-quickstart";

    private static final OpenAPISupport.Builder OPENAPI_SUPPORT_BUILDER
            = OpenAPISupport.builder()
                    .staticFile("src/test/resources/openapi-quickstart.yml")
                    .webContext(QUICKSTART_PATH);

    public ServerTest() {
    }

    @BeforeAll
    public static void startup() {
        webServer = TestUtil.startServer(OPENAPI_SUPPORT_BUILDER);
    }


    @AfterAll
    public static void shutdown() {
        TestUtil.shutdownServer(webServer);
    }


    /**
     * Accesses the OpenAPI endpoint, requesting a YAML response payload, and
     * makes sure that navigating among the YAML yields what we expect.
     *
     * @throws Exception in case of errors sending the request or reading the
     * response
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleAsYAML() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                webServer.port(),
                "GET",
                QUICKSTART_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        Map<String, Object> openAPIDocument = TestUtil.yamlFromResponse(cnx);

        ArrayList<Map<String, Object>> servers = TestUtil.as(
                ArrayList.class, openAPIDocument.get("servers"));
        Map<String, Object> server = servers.get(0);
        assertEquals("http://localhost:8000", server.get("url"), "unexpected URL");
        assertEquals("Local test server", server.get("description"), "unexpected description");

        Map<String, Object> paths = TestUtil.as(Map.class, openAPIDocument.get("paths"));
        Map<String, Object> setGreetingPath = TestUtil.as(Map.class, paths.get("/greet/greeting"));
        Map<String, Object> put = TestUtil.as(Map.class, setGreetingPath.get("put"));
        assertEquals("Sets the greeting prefix", put.get("summary"));
        Map<String, Object> requestBody = TestUtil.as(Map.class, put.get("requestBody"));
        assertTrue(Boolean.class.cast(requestBody.get("required")));
        Map<String, Object> content = TestUtil.as(Map.class, requestBody.get("content"));
        Map<String, Object> applicationJson = TestUtil.as(Map.class, content.get("application/json"));
        Map<String, Object> schema = TestUtil.as(Map.class, applicationJson.get("schema"));

        assertEquals("object", schema.get("type"));
    }

    /**
     * Tests the OpenAPI support by converting the response payload as YAML and
     * then creating a {@code Config} instance from that YAML for ease of
     * accessing its values in the test.
     *
     * @throws Exception in case of errors sending the request or receiving the
     * response
     */
    @Test
    public void testSimpleAsConfig() throws Exception {
        HttpURLConnection cnx = TestUtil.getURLConnection(
                webServer.port(),
                "GET",
                QUICKSTART_PATH,
                MediaType.APPLICATION_OPENAPI_YAML);
        Config c = TestUtil.configFromResponse(cnx);
        assertEquals("Sets the greeting prefix",
                TestUtil.fromConfig(c, "paths./greet/greeting.put.summary"));
        assertEquals("string",
                TestUtil.fromConfig(c,
                        "paths./greet/greeting.put.requestBody.content."
                            + "application/json.schema.properties.greeting.type"));
    }

    /**
     * Makes sure that the response content type is consistent with the Accept
     * media type.
     *
     * @throws Exception in case of errors sending the request or receiving the
     * response
     */
    @Test
    public void checkExplicitResponseMediaType() throws Exception {
        connectAndConsumePayload(MediaType.APPLICATION_OPENAPI_YAML);
        connectAndConsumePayload(MediaType.APPLICATION_YAML);
        connectAndConsumePayload(MediaType.APPLICATION_OPENAPI_JSON);
        connectAndConsumePayload(MediaType.APPLICATION_JSON);
    }

    /**
     * Makes sure that the response is correct if the request specified no
     * explicit Accept.
     *
     * @throws Exception error sending the request or receiving the response
     */
    @Test
    public void checkDefaultResponseMediaType() throws Exception {
        connectAndConsumePayload(null);
    }

    private static void connectAndConsumePayload(MediaType mt) throws Exception {
        TestUtil.connectAndConsumePayload(webServer.port(), QUICKSTART_PATH, mt);
    }
}
