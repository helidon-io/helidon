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

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.internal.YamlConfigParser;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.yaml.snakeyaml.Yaml;

/**
 * Starts a server with several OpenAPI endpoints to test various behaviors.
 */
public class ServerTest {

    private static final Logger LOGGER = Logger.getLogger(ServerTest.class.getName());

    private static WebServer webServer;

    private static final String QUICKSTART_PATH = "/openapi-quickstart";

    private static final JsonReaderFactory JSON_READER_FACTORY
            = Json.createReaderFactory(Collections.emptyMap());

    /*
     * A normal app would not do this, but we configure the test server to
     * publish numerous OpenAPI documents at different endpoints so we can test
     * that the various endpoints return the correct information from each of
     * the different OpenAPI endpoints.
     */
    private static final OpenAPISupport.Builder OPENAPI_SUPPORT_BUILDER
            = OpenAPISupport.builder()
                    .staticFile("src/test/resources/openapi-quickstart.yml")
                    .webContext(QUICKSTART_PATH);

    public ServerTest() {
    }

    @BeforeAll
    public static void startup() {
        webServer = startServer(OPENAPI_SUPPORT_BUILDER);
    }

    public static WebServer startServer(OpenAPISupport.Builder builder) {
        try {
            return startServer(0, builder);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException("Error starting server for test", ex);
        }
    }

    @AfterAll
    public static void shutdown() {
        shutdownServer(webServer);
    }

    public static void shutdownServer(WebServer ws) {
        if (ws != null) {
            try {
                stopServer(ws);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new RuntimeException("Error shutting down server for test", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleAsYAML() throws Exception {
        HttpURLConnection cnx = getURLConnection(webServer, "GET", QUICKSTART_PATH, MediaType.APPLICATION_OPENAPI_YAML);
        Map<String, Object> openAPIDocument = yamlFromResponse(cnx);

        ArrayList<Map<String, Object>> servers = as(ArrayList.class, openAPIDocument.get("servers"));
        Map<String, Object> server = servers.get(0);
        assertEquals("http://localhost:8000", server.get("url"), "unexpected URL");
        assertEquals("Local test server", server.get("description"), "unexpected description");

        Map<String, Object> paths = as(Map.class, openAPIDocument.get("paths"));
        Map<String, Object> setGreetingPath = as(Map.class, paths.get("/greet/greeting"));
        Map<String, Object> put = as(Map.class, setGreetingPath.get("put"));
        assertEquals("Sets the greeting prefix", put.get("summary"));
        Map<String, Object> requestBody = as(Map.class, put.get("requestBody"));
        assertTrue(Boolean.class.cast(requestBody.get("required")));
        Map<String, Object> content = as(Map.class, requestBody.get("content"));
        Map<String, Object> applicationJson = as(Map.class, content.get("application/json"));
        Map<String, Object> schema = as(Map.class, applicationJson.get("schema"));

        assertEquals("object", schema.get("type"));
    }

    @Test
    public void testSimpleAsConfig() throws Exception {
        HttpURLConnection cnx = getURLConnection(webServer, "GET", QUICKSTART_PATH, MediaType.APPLICATION_OPENAPI_YAML);
        Config c = configFromResponse(cnx);
        assertEquals("Sets the greeting prefix", fromConfig(c, "paths./greet/greeting.put.summary"));
        assertEquals("string", fromConfig(c, "paths./greet/greeting.put.requestBody.content.application/json.schema.properties.greeting.type"));
    }

    @Test
    public void checkExplicitResponseMediaType() throws Exception {
        validateResponseMediaTypeAndPayload(MediaType.APPLICATION_OPENAPI_YAML);
        validateResponseMediaTypeAndPayload(MediaType.APPLICATION_YAML);
        validateResponseMediaTypeAndPayload(MediaType.APPLICATION_OPENAPI_JSON);
        validateResponseMediaTypeAndPayload(MediaType.APPLICATION_JSON);
    }

    @Test
    public void checkDefaultResponseMediaType() throws Exception {
        validateResponseMediaTypeAndPayload(null);
    }

    /**
     * Makes sure that the response is 200 and that the content type MediaType
     * is consistent with the expected one, returning the actual MediaType from
     * the response.
     *
     * @param cnx {@code HttpURLConnection} with the response to validate
     * @param expectedMediaType media type with which the actual one should be
     * consistent
     * @return actual media type
     * @throws Exception in case of errors reading the content type from the
     * response
     */
    public static MediaType validateResponseMediaType(
            HttpURLConnection cnx, MediaType expectedMediaType) throws Exception {
        assertEquals(Http.Status.OK_200.code(), cnx.getResponseCode(),
                "Unexpected response code");
        MediaType expectedMT = expectedMediaType != null ? expectedMediaType : OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE;
        MediaType actualMT = mediaTypeFromResponse(cnx);
        assertTrue(expectedMT.test(actualMT),
                "Expected response media type " + expectedMT.toString()
                + " but received " + actualMT.toString());

        return actualMT;
    }

    /**
     * Returns a {@code HttpURLConnection} for the requested method and path
     * from the specified {@link WebServer}.
     *
     * @param ws WebServer to use (we need the port)
     * @param method HTTP method to use in building the connection
     * @param path path to the resource in the web server
     * @param mediaType {@code MediaType} to be Accepted
     * @return the connection to the server and path
     * @throws Exception in case of errors creating the connection
     */
    public static HttpURLConnection getURLConnection(WebServer ws, String method, String path, MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + ws.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (mediaType != null) {
            conn.setRequestProperty("Accept", mediaType.toString());
        }
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    /**
     * Returns the response payload in the specified connection as a
     * {@code Yaml} object.
     *
     * @param cnx the HttpURLConnection containing the response
     * @return the YAML map (created by snakeyaml) from the HTTP response
     * payload
     * @throws IOException in case of errors reading the response
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> yamlFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType returnedMediaType = mediaTypeFromResponse(cnx);

        Yaml yaml = new Yaml();
        Charset cs = Charset.defaultCharset();
        if (returnedMediaType.charset().isPresent()) {
            cs = Charset.forName(returnedMediaType.charset().get());
        }
        return (Map<String, Object>) yaml.load(
                new InputStreamReader(cnx.getInputStream(), cs));
    }

    /**
     * Returns the response payload in the specified connection as a
     * {@code JsonStructure}.
     *
     * @param cnx the HttpURLConnection containing the response
     * @return {@code JsonStructure} representing the response payload
     * @throws IOException in case of errors reading the response
     */
    public static JsonStructure jsonFromResponse(HttpURLConnection cnx) throws IOException {
        JsonReader reader = JSON_READER_FACTORY.createReader(cnx.getInputStream());
        JsonStructure result = reader.read();
        return result;
    }

    /**
     * Converts a JSON pointer possibly containing slashes and tildes into a
     * JSON pointer with such characters properly escaped.
     *
     * @param pointer original JSON pointer expression
     * @return escaped (if needed) JSON pointer
     */
    public static String escapeForJsonPointer(String pointer) {
        return pointer.replaceAll("\\~", "~0").replaceAll("\\/", "~1");
    }

    private static MediaType validateResponseMediaTypeAndPayload(MediaType expectedMediaType) throws Exception {
        HttpURLConnection cnx = getURLConnection(webServer, "GET", QUICKSTART_PATH, expectedMediaType);
        MediaType actualMT= validateResponseMediaType(cnx, expectedMediaType);
        if (actualMT.test(MediaType.APPLICATION_OPENAPI_YAML)
                || actualMT.test(MediaType.APPLICATION_YAML)) {
            yamlFromResponse(cnx);
        } else if (actualMT.test(MediaType.APPLICATION_OPENAPI_JSON)
                || actualMT.test(MediaType.APPLICATION_JSON)) {
            jsonFromResponse(cnx);
        } else {
            throw new IllegalArgumentException(
                    "Expected either JSON or YAML response but received " + actualMT.toString());
        }
        return actualMT;
    }

    private String fromConfig(Config c, String key) {
        Config v = c.get(key);
        if (!v.exists()) {
            throw new IllegalArgumentException("Requested key not found: " + key);
        }
        return v.asString().get();
    }

    /**
     * Represents an OpenAPI document HTTP response as a {@code Config} instance
     * to simplify access to deeply-nested values.
     *
     * @param cnx the HttpURLConnection which already has the response to
     * process
     * @return Config representing the OpenAPI document content
     * @throws IOException in case of errors reading the returned payload as
     * config
     */
    private Config configFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType mt = mediaTypeFromResponse(cnx);
        String configMT = MediaType.APPLICATION_OPENAPI_YAML.test(mt)
                ? YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML : MediaType.APPLICATION_JSON.toString();
        String yaml = stringYAMLFromResponse(cnx);
        return Config.create(ConfigSources.create(yaml, configMT));
    }

    /**
     * Returns the {@code MediaType} instance conforming to the HTTP response
     * content type.
     *
     * @param cnx the HttpURLConnection from which to get the content type
     * @return the MediaType corresponding to the content type in the response
     */
    public static MediaType mediaTypeFromResponse(HttpURLConnection cnx) {
        MediaType returnedMediaType = MediaType.parse(cnx.getContentType());
        if (!returnedMediaType.charset().isPresent()) {
            returnedMediaType = MediaType.builder()
                    .type(returnedMediaType.type())
                    .subtype(returnedMediaType.subtype())
                    .charset(Charset.defaultCharset().name())
                    .build();
        }
        return returnedMediaType;
    }

    /**
     * Represents the HTTP response payload as a String.
     *
     * @param cnx the HttpURLConnection from which to get the response payload
     * @return String representation of the OpenAPI document as a String
     * @throws IOException in case of errors reading the HTTP response payload
     */
    private String stringYAMLFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType returnedMediaType = mediaTypeFromResponse(cnx);
        assertTrue(MediaType.APPLICATION_OPENAPI_YAML.test(returnedMediaType),
                "Unexpected returned media type");
        return stringFromResponse(cnx, returnedMediaType);
    }

    private static String stringFromResponse(HttpURLConnection cnx, MediaType mediaType) throws IOException {
        try (InputStreamReader isr
                = new InputStreamReader(cnx.getInputStream(), mediaType.charset().get())) {
            StringBuilder sb = new StringBuilder();
            CharBuffer cb = CharBuffer.allocate(1024);
            while (isr.read(cb) != -1) {
                cb.flip();
                sb.append(cb);
            }
            return sb.toString();
        }
    }

    private <T> T as(Class<T> c, Object o) {
        return c.cast(o);
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1, the
     * port is dynamically selected
     * @param openAPIBuilders OpenAPISupport.Builder instances to use in
     * starting the server
     * @throws Exception in case of an error
     */
    private static WebServer startServer(int port, OpenAPISupport.Builder... openAPIBuilders)
            throws InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.create(
                ServerConfiguration.builder().port(port).build(),
                Routing.builder().register(openAPIBuilders).build())
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);

        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }

    private static void stopServer(WebServer server)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (server != null) {
            server.shutdown()
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
        }
    }

}
