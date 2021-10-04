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
package io.helidon.microprofile.openapi;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Map;

import javax.ws.rs.core.Application;

import io.helidon.common.http.MediaType;
import io.helidon.config.Config;
import io.helidon.microprofile.server.Server;

import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Useful utility methods during testing.
 */
public class TestUtil {

    /**
     * Starts the MP server running the specific application.
     * <p>
     * The server will use the default MP config.
     *
     * @param helidonConfig the Helidon configuration to use in preparing the server
     * @param appClasses application classes to serve
     * @return the started MP {@code Server} instance
     */
    public static Server startServer(Config helidonConfig, Class<? extends Application>... appClasses) {
        Server.Builder builder = Server.builder()
                .port(0)
                .config(helidonConfig);
        for (Class<? extends Application> appClass : appClasses) {
            builder.addApplication(appClass);
        }
        return builder
                .build()
                .start();
    }

    /**
     * Cleans up, stopping the server and disconnecting the connection.
     *
     * @param server the {@code Server} to stop
     * @param cnx the connection to disconnect
     */
    public static void cleanup(Server server, HttpURLConnection cnx) {
        if (cnx != null) {
            cnx.disconnect();
        }
        if (server != null) {
            server.stop();
        }
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
    public static String stringYAMLFromResponse(HttpURLConnection cnx) throws IOException {
        MediaType returnedMediaType = mediaTypeFromResponse(cnx);
        assertTrue(MediaType.APPLICATION_OPENAPI_YAML.test(returnedMediaType),
                "Unexpected returned media type");
        return stringFromResponse(cnx, returnedMediaType);
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
        try (InputStream is = cnx.getInputStream(); InputStreamReader isr =  new InputStreamReader(is, cs)) {
            return (Map<String, Object>) yaml.load(isr);
        }
    }

    /**
     * Treats the provided {@code Map} as a YAML map and navigates through it
     * using the dotted-name convention as expressed in the {@code dottedPath}
     * argument, finally casting the value retrieved from the last segment of
     * the path as the specified type and returning that cast value.
     *
     * @param <T> type to which the final value will be cast
     * @param map the YAML-inspired map
     * @param dottedPath navigation path to the item of interest in the YAML
     * maps-of-maps; note that the {@code dottedPath} must not use dots except
     * as path segment separators
     * @param cl {@code Class} for the return type {@code <T>}
     * @return value from the lowest-level map retrieved using the last path
     * segment, cast to the specified type
     */
    @SuppressWarnings(value = "unchecked")
    public static <T> T fromYaml(Map<String, Object> map, String dottedPath, Class<T> cl) {
        Map<String, Object> originalMap = map;
        String[] segments = dottedPath.split("\\.");
        for (int i = 0; i < segments.length - 1; i++) {
            map = (Map<String, Object>) map.get(segments[i]);
            if (map == null) {
                fail("Traversing dotted path " + dottedPath + " segment " + segments[i] + " not found in parsed map "
                        + originalMap);
            }
        }
        return cl.cast(map.get(segments[segments.length - 1]));
    }
}
