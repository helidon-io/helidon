/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.harness;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.lang.System.Logger.Level;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webclient.http1.Http1ClientRequest;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.junit.jupiter.api.Assertions;

/**
 * Web client to access all services of remote test application.
 */
public class TestClient {

    private static final System.Logger LOGGER = System.getLogger(TestClient.class.getName());
    private static final String UTF_8_STR = StandardCharsets.UTF_8.toString();

    private final Http1Client webClient;

    TestClient(Http1Client webClient) {
        this.webClient = webClient;
    }

    Http1ClientRequest clientGetBuilderWithPath(String service, String method) {
        StringBuilder sb = new StringBuilder(service.length() + (method != null ? method.length() : 0) + 2);
        sb.append('/');
        sb.append(service);
        if (method != null) {
            sb.append('/');
            sb.append(method);
        }
        return webClient.get(sb.toString());
    }

    private static String encode(String str) {
        try {
            return URLEncoder.encode(str, UTF_8_STR);
        } catch (UnsupportedEncodingException ex) {
            Assertions.fail(String.format("URL fragment encoding failed: %s", ex.getMessage()));
        }
        return "";
    }

    private static Exception deserialize(JsonObject response) {
        Exception root = null;
        Exception ex = null;
        JsonArray stackTraces = response.getJsonArray("stacktrace");
        for (JsonObject stackTrace : stackTraces.getValuesAs(JsonObject.class)) {
            List<StackTraceElement> elements = new ArrayList<>();
            for (JsonObject elt : stackTrace.getJsonArray("trace").getValuesAs(JsonObject.class)) {
                elements.add(new StackTraceElement(
                        toString(elt.get("class")),
                        toString(elt.get("method")),
                        toString(elt.get("file")),
                        elt.getInt("line")));
            }
            Exception cause = new Exception(String.format("Deserialized: class=%s, message=%s",
                    toString(stackTrace.get("class")), toString(stackTrace.get("message"))));
            cause.setStackTrace(elements.toArray(new StackTraceElement[0]));
            if (ex == null) {
                root = cause;
            } else {
                ex.initCause(cause);
            }
            ex = cause;
        }
        return root;
    }

    private static String toString(JsonValue value) {
        if (value.getValueType() == JsonValue.ValueType.STRING) {
            return ((JsonString) value).getString();
        }
        return "?";
    }

    /**
     * Creates new web client builder instance.
     *
     * @return new web client builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Call remote test service method and return its data.
     *
     * @param service remote service name
     * @param method  remote test method name
     * @param params  remote test method query parameters
     * @return data returned by remote test service method
     */
    public JsonValue callServiceAndGetData(String service, String method, Map<String, String> params) {
        return evaluateServiceCallResult(
                callService(
                        clientGetBuilderWithPath(service, method),
                        params));
    }

    /**
     * Call remote test service method and return its data.
     * No query parameters are passed.
     *
     * @param service remote service name
     * @param method  remote test method name
     * @return data returned by remote test service method
     */
    public JsonValue callServiceAndGetData(String service, String method) {
        return callServiceAndGetData(service, method, null);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done.
     *
     * @param service remote service name
     * @param method  remote test method name
     * @param params  remote test method query parameters
     * @return data returned by remote service
     */
    public JsonObject callServiceAndGetRawData(String service, String method, Map<String, String> params) {
        Http1ClientRequest rb = clientGetBuilderWithPath(service, method);
        rb.headers().accept(MediaTypes.APPLICATION_JSON);
        return callService(rb, params);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done. No query parameters are passed.
     *
     * @param service remote service name
     * @param method  remote test method name
     * @return data returned by remote service
     */
    public JsonObject callServiceAndGetRawData(String service, String method) {
        return callServiceAndGetRawData(service, method, null);
    }

    JsonObject callService(Http1ClientRequest clientRequest, Map<String, String> params) {
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                clientRequest.queryParam(entry.getKey(), encode(entry.getValue()));
            }
        }
        String response = clientRequest.requestEntity(String.class);
        try {
            JsonReader jsonReader = Json.createReader(new StringReader(response));
            JsonValue jsonContent = jsonReader.read();
            if (Objects.requireNonNull(jsonContent.getValueType()) == JsonValue.ValueType.OBJECT) {
                return jsonContent.asJsonObject();
            }
            throw new HelidonTestException(
                    String.format(
                            "Expected JSON object, but got JSON %s",
                            jsonContent.getValueType().name().toLowerCase()));
        } catch (JsonException t) {
            LOGGER.log(Level.WARNING, () -> String.format("Caught %s when parsing response: %s",
                    t.getClass().getSimpleName(),
                    response), t);
            throw new HelidonTestException(
                    String.format(
                            "Caught %s when parsing response: %s",
                            t.getClass().getSimpleName(),
                            response),
                    t);
        }
    }

    JsonValue evaluateServiceCallResult(JsonObject data) {
        String status = data.getString("status");
        switch (status) {
            case "OK" -> {
                return data.get("data");
            }
            case "exception" -> throw new HelidonTestException("Remote test execution failed.", deserialize(data));
            default -> throw new HelidonTestException("Unknown response content: " + data);
        }
    }

    /**
     * Remote test web client builder.
     */
    public static class Builder {

        @SuppressWarnings("HttpUrlsUsage")
        private static final String HTTP_PREFIX = "http://";

        private String host;
        private int port;

        Builder() {
            this.host = "localhost";
            this.port = 8080;
        }

        /**
         * Set test application URL host.
         *
         * @param host test application URL host
         * @return updated {@code TestClient} builder instance
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set test application URL port.
         *
         * @param port test application URL port
         * @return updated {@code TestClient} builder instance
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Set test application URL service.
         * Setting service name will result in building extended test client
         * with service name support.
         *
         * @param service test application URL service
         * @return updated {@code TestServiceClient} builder instance
         */
        public TestServiceClient.Builder service(String service) {
            return new TestServiceClient.Builder(this, service);
        }

        /**
         * Builds web client initialized with parameters stored in this builder.
         *
         * @return new {@link Http1Client} instance
         */
        Http1Client buildWebClient() {
            return Http1Client.builder()
                    .baseUri(HTTP_PREFIX + host + ':' + port)
                    .readTimeout(Duration.ofMinutes(10))
                    .addMediaSupport(JsonpSupport.create())
                    .build();
        }

        /**
         * Builds test web client initialized with parameters stored in this builder.
         *
         * @return new {@link TestClient} instance
         */
        public TestClient build() {
            return new TestClient(buildWebClient());
        }
    }
}
