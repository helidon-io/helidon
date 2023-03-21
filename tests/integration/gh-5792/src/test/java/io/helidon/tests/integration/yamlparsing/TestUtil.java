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
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Collections;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.JsonReaderFactory;
import javax.json.JsonStructure;

import io.helidon.common.http.MediaType;

class TestUtil {

    private static final JsonReaderFactory JSON_READER_FACTORY
            = Json.createReaderFactory(Collections.emptyMap());

    /**
     * Returns a {@code HttpURLConnection} for the requested method and path and
     * {code @MediaType} from the specified {@link io.helidon.webserver.WebServer}.
     *
     * @param port port to connect to
     * @param method HTTP method to use in building the connection
     * @param path path to the resource in the web server
     * @param mediaType {@code MediaType} to be Accepted
     * @return the connection to the server and path
     * @throws Exception in case of errors creating the connection
     */
    static HttpURLConnection getURLConnection(
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
     * Returns a {@code String} resulting from interpreting the response payload
     * in the specified connection according to the expected {@code MediaType}.
     *
     * @param cnx {@code HttpURLConnection} with the response
     * payload
     * @return {@code String} of the payload interpreted according to the
     * specified {@code MediaType}
     * @throws IOException in case of errors reading the response payload
     */
    public static String stringFromResponse(HttpURLConnection cnx) throws IOException {
        try (final InputStreamReader isr = new InputStreamReader(
                cnx.getInputStream(), Charset.defaultCharset())) {
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
}
