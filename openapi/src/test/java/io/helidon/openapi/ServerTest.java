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

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.config.yaml.internal.YamlConfigParser;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerConfiguration;
import io.helidon.webserver.WebServer;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    /*
     * A normal app would not do this, but we configure the test server to
     * publish numerous OpenAPI documents at different endpoints so we can test
     * that the various endpoints return the correct information from each
     * of the different OpenAPI endpoints.
     */
    private static final OpenAPISupport.Builder[] OPENAPI_SUPPORT_BUILDERS = {
//        OpenAPISupport.builder()
//                .config(Config.create(ConfigSources.classpath("simple.properties"))),
        OpenAPISupport.builder()
                .staticFile("src/test/resources/openapi-quickstart.yml")
                .webContext(QUICKSTART_PATH)
    };

    public ServerTest() {
    }

    @BeforeAll
    public static void startup() {
        try {
            webServer = startServer(0, OPENAPI_SUPPORT_BUILDERS);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException("Error starting server for test", ex);
        }
    }

    @AfterAll
    public static void shutdown() {
        if (webServer != null) {
            try {
                stopServer(webServer);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new RuntimeException("Error shutting down server for test", ex);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSimpleAsYAML() throws Exception {
        HttpURLConnection cnx = getURLConnection("GET", QUICKSTART_PATH, MediaType.APPLICATION_OPENAPI_YAML);
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
    }

    @Test
    public void testSimpleAsConfig() throws Exception {
        HttpURLConnection cnx = getURLConnection("GET", QUICKSTART_PATH, MediaType.APPLICATION_OPENAPI_YAML);
        Config c = configFromResponse(cnx);
        assertEquals("Sets the greeting prefix", fromConfig(c, "paths./greet/greeting.put.summary"));
        assertEquals("string", fromConfig(c, "paths./greet/greeting.put.requestBody.content.application/json.schema.properties.greeting.type"));
    }

    @Test
    public void checkExplicitResponseMediaType() throws Exception {
        validateResponseMediaType(MediaType.APPLICATION_OPENAPI_YAML);
        validateResponseMediaType(MediaType.APPLICATION_YAML);
        validateResponseMediaType(MediaType.APPLICATION_OPENAPI_JSON);
        validateResponseMediaType(MediaType.APPLICATION_JSON);
    }

    @Test
    public void checkDefaultResponseMediaType() throws Exception {
        validateResponseMediaType(null);
    }

    private void validateResponseMediaType(MediaType mt) throws Exception {
        HttpURLConnection cnx = getURLConnection("GET", QUICKSTART_PATH, mt);
        assertEquals(Http.Status.OK_200.code(), cnx.getResponseCode(),
                "Unexpected response code");
        MediaType expectedMT = mt != null ? mt : OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE;
        MediaType actualMT = mediaTypeFromResponse(cnx);
        assertTrue(expectedMT.test(actualMT),
                "Expected response media type " + expectedMT.toString()
        + " but received " + actualMT.toString());
        yamlFromResponse(cnx); // to allow Java to reuse the connection
    }

    private String fromConfig(Config c, String key) {
        Config v = c.get(key);
        if (!v.exists()) {
            throw new IllegalArgumentException("Requested key not found: " + key);
        }
        return v.asString().get();
    }

    private HttpURLConnection getURLConnection(String method, String path, MediaType mediaType) throws Exception {
        URL url = new URL("http://localhost:" + webServer.port() + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        if (mediaType != null) {
            conn.setRequestProperty("Accept", mediaType.toString());
        }
        System.out.println("Connecting: " + method + " " + url);
        return conn;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> yamlFromResponse(HttpURLConnection cnx) throws IOException {
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
     * Represents an OpenAPI document HTTP response as a {@code Config} instance
     * to simplify access to deeply-nested values.
     *
     * @param cnx the HttpURLConnection which already has the response to process
     * @return Config representing the OpenAPI document content
     * @throws IOException in case of errors reading the returned payload as config
     */
    private Config configFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType mt = mediaTypeFromResponse(cnx);
        String configMT = MediaType.APPLICATION_OPENAPI_YAML.test(mt)
                ? YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML : MediaType.APPLICATION_JSON.toString();
        String yaml = stringYAMLFromResponse(cnx);
        return Config.create(ConfigSources.create(yaml, configMT));
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

        try (InputStreamReader isr =
                new InputStreamReader(cnx.getInputStream(), returnedMediaType.charset().get())) {
            StringBuilder sb = new StringBuilder();
            CharBuffer cb = CharBuffer.allocate(1024);
            while (isr.read(cb) != -1) {
                cb.flip();
                sb.append(cb);
            }
            return sb.toString();
        }
    }

    /**
     * Returns the {@code MediaType} instance conforming to the HTTP response
     * content type.
     *
     * @param cnx the HttpURLConnection from which to get the content type
     * @return the MediaType corresponding to the content type in the response
     */
    private MediaType mediaTypeFromResponse(HttpURLConnection cnx) {
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

    private <T> T as(Class<T> c, Object o) {
        return c.cast(o);
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1,
     *             the port is dynamically selected
     * @param openAPIBuilders OpenAPISupport.Builder instances to use in starting the server
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
