/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
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
import io.helidon.config.yaml.YamlConfigParser;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;

import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Various utility methods used by OpenAPI tests.
 */
public class TestUtil {

    private static final JsonReaderFactory JSON_READER_FACTORY
            = Json.createReaderFactory(Collections.emptyMap());

    private static final Logger LOGGER = Logger.getLogger(TestUtil.class.getName());

    /**
     * Starts the web server at an available port and sets up OpenAPI using the
     * supplied builder.
     *
     * @param builder the {@code OpenAPISupport.Builder} to set up for the
     * server.
     * @return the {@code WebServer} set up with OpenAPI support
     */
    public static WebServer startServer(OpenAPISupport.Builder builder) {
        try {
            return startServer(0, builder);
        } catch (InterruptedException | ExecutionException | TimeoutException ex) {
            throw new RuntimeException("Error starting server for test", ex);
        }
    }

    /**
     * Represents the HTTP response payload as a String.
     *
     * @param cnx the HttpURLConnection from which to get the response payload
     * @return String representation of the OpenAPI document as a String
     * @throws IOException in case of errors reading the HTTP response payload
     */
    public static String stringYAMLFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType returnedMediaType = mediaTypeFromResponse(cnx);
        assertTrue(MediaType.APPLICATION_OPENAPI_YAML.test(returnedMediaType),
                "Unexpected returned media type");
        return stringFromResponse(cnx, returnedMediaType);
    }

    /**
     * Connects to localhost at the specified port, sends a request using the
     * specified method, and consumes the response payload as the indicated
     * media type, returning the actual media type reported in the response.
     *
     * @param port port with which to create the connection
     * @param path URL path to access on the web server
     * @param expectedMediaType the {@code MediaType} with which the response
     * must be consistent
     * @return actual {@code MediaType}
     * @throws Exception in case of errors sending the request or receiving the
     * response
     */
    public static MediaType connectAndConsumePayload(
            int port, String path, MediaType expectedMediaType) throws Exception {
        HttpURLConnection cnx = getURLConnection(port, "GET", path, expectedMediaType);
        MediaType actualMT = validateResponseMediaType(cnx, expectedMediaType);
        if (actualMT.test(MediaType.APPLICATION_OPENAPI_YAML) || actualMT.test(MediaType.APPLICATION_YAML)) {
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
     * Represents an OpenAPI document HTTP response as a {@code Config} instance
     * to simplify access to deeply-nested values.
     *
     * @param cnx the HttpURLConnection which already has the response to
     * process
     * @return Config representing the OpenAPI document content
     * @throws IOException in case of errors reading the returned payload as
     * config
     */
    public static Config configFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType mt = mediaTypeFromResponse(cnx);
        String configMT = MediaType.APPLICATION_OPENAPI_YAML.test(mt)
                ? YamlConfigParser.MEDIA_TYPE_APPLICATION_YAML
                : MediaType.APPLICATION_JSON.toString();
        String yaml = stringYAMLFromResponse(cnx);
        return Config.create(ConfigSources.create(yaml, configMT));
    }

    /**
     * Returns the response payload from the specified connection as a snakeyaml
     * {@code Yaml} object.
     *
     * @param cnx the {@code HttpURLConnection} containing the response
     * @return the YAML {@code Map<String, Object>} (created by snakeyaml) from
     * the HTTP response payload
     * @throws IOException in case of errors reading the response
     */
    @SuppressWarnings(value = "unchecked")
    public static Map<String, Object> yamlFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType returnedMediaType = mediaTypeFromResponse(cnx);
        Yaml yaml = new Yaml();
        Charset cs = Charset.defaultCharset();
        if (returnedMediaType.charset().isPresent()) {
            cs = Charset.forName(returnedMediaType.charset().get());
        }
        return (Map<String, Object>) yaml.load(new InputStreamReader(cnx.getInputStream(), cs));
    }

    /**
     * Shuts down the specified web server.
     *
     * @param ws the {@code WebServer} instance to stop
     */
    public static void shutdownServer(WebServer ws) {
        if (ws != null) {
            try {
                stopServer(ws);
            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                throw new RuntimeException("Error shutting down server for test", ex);
            }
        }
    }

    /**
     * Returns the string values from the specified key in the {@code Config},
     * ensuring that the key exists first.
     *
     * @param c the {@code Config} object to query
     * @param key the key to access in the {@code Config} object
     * @return the {@code String} value from the {@code Config} value
     */
    public static String fromConfig(Config c, String key) {
        Config v = c.get(key);
        if (!v.exists()) {
            throw new IllegalArgumentException("Requested key not found: " + key);
        }
        return v.asString().get();
    }

    /**
     * Returns the response payload in the specified connection as a
     * {@code JsonStructure} instance.
     *
     * @param cnx the {@code HttpURLConnection} containing the response
     * @return {@code JsonStructure} representing the response payload
     * @throws IOException in case of errors reading the response
     */
    public static JsonStructure jsonFromResponse(HttpURLConnection cnx) throws IOException {
        JsonReader reader = JSON_READER_FACTORY.createReader(cnx.getInputStream());
        JsonStructure result = reader.read();
        reader.close();
        return result;
    }

    static JsonStructure jsonFromReader(Reader reader) {
        JsonReader jsonReader = JSON_READER_FACTORY.createReader(reader);
        JsonStructure result = jsonReader.read();
        jsonReader.close();
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

    /**
     * Makes sure that the response is 200 and that the content type MediaType
     * is consistent with the expected one, returning the actual MediaType from
     * the response and leaving the payload ready for consumption.
     *
     * @param cnx {@code HttpURLConnection} with the response to validate
     * @param expectedMediaType {@code MediaType} with which the actual one
     * should be consistent
     * @return actual media type
     * @throws Exception in case of errors reading the content type from the
     * response
     */
    public static MediaType validateResponseMediaType(
            HttpURLConnection cnx,
            MediaType expectedMediaType) throws Exception {
        assertEquals(Http.Status.OK_200.code(), cnx.getResponseCode(),
                "Unexpected response code");
        MediaType expectedMT = expectedMediaType != null
                ? expectedMediaType
                : OpenAPISupport.DEFAULT_RESPONSE_MEDIA_TYPE;
        MediaType actualMT = mediaTypeFromResponse(cnx);
        assertTrue(expectedMT.test(actualMT),
                "Expected response media type "
                        + expectedMT.toString()
                        + " but received "
                        + actualMT.toString());
        return actualMT;
    }

    /**
     * Returns a {@code HttpURLConnection} for the requested method and path and
     * {code @MediaType} from the specified {@link WebServer}.
     *
     * @param port port to connect to
     * @param method HTTP method to use in building the connection
     * @param path path to the resource in the web server
     * @param mediaType {@code MediaType} to be Accepted
     * @return the connection to the server and path
     * @throws Exception in case of errors creating the connection
     */
    public static HttpURLConnection getURLConnection(
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

    /**
     * Stop the web server.
     *
     * @param server the {@code WebServer} to stop
     * @throws InterruptedException if the stop operation was interrupted
     * @throws ExecutionException if the stop operation failed as it ran
     * @throws TimeoutException if the stop operation timed out
     */
    public static void stopServer(WebServer server) throws
            InterruptedException, ExecutionException, TimeoutException {
        if (server != null) {
            server.shutdown().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Start the Web Server
     *
     * @param port the port on which to start the server; if less than 1, the
     * port is dynamically selected
     * @param openAPIBuilders OpenAPISupport.Builder instances to use in
     * starting the server
     * @return {@code WebServer} that has been started
     * @throws java.lang.InterruptedException if the start was interrupted
     * @throws java.util.concurrent.ExecutionException if the start failed
     * @throws java.util.concurrent.TimeoutException if the start timed out
     */
    public static WebServer startServer(
            int port,
            OpenAPISupport.Builder... openAPIBuilders) throws
            InterruptedException, ExecutionException, TimeoutException {
        WebServer result = WebServer.builder(Routing.builder()
                        .register(openAPIBuilders)
                        .build())
                .port(port)
                .build()
                .start()
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        LOGGER.log(Level.INFO, "Started server at: https://localhost:{0}", result.port());
        return result;
    }

    /**
     * Returns a {@code String} resulting from interpreting the response payload
     * in the specified connection according to the expected {@code MediaType}.
     *
     * @param cnx {@code HttpURLConnection} with the response
     * @param mediaType {@code MediaType} to use in interpreting the response
     * payload
     * @return {@code String} of the payload interpreted according to the
     * specified {@code MediaType}
     * @throws IOException in case of errors reading the response payload
     */
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

    /**
     * Returns an instance of the requested type given the input object.
     *
     * @param <T> expected type
     * @param c the {@code Class} for the expected type
     * @param o the {@code Object} to be cast to the expected type
     * @return the object, cast to {@code T}
     */
    public static <T> T as(Class<T> c, Object o) {
        return c.cast(o);
    }
}
