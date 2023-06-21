/*
 * Copyright (c) 2020, 2023 Oracle and/or its affiliates.
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

package io.helidon.jersey.connector;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.Response;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;

import org.glassfish.jersey.client.ClientConfig;
import org.junit.jupiter.api.AfterAll;

public abstract class AbstractTest {

    protected static WireMockServer wireMockServer;

    // The port to match wiremock port in MP Rest Client TCK
    protected static final int PORT = 8765;

    protected WebTarget target(String uri) {
        ClientConfig config = new ClientConfig();
        config.connectorProvider(new HelidonConnectorProvider());
        Client client = ClientBuilder.newClient(config);
        return client.target(getBaseUri()).path(uri);
    }

    protected static String getBaseUri() {
        return "http://localhost:" + PORT;
    }

    protected static void setup(Rules rules, Extension[] extensions) {
        wireMockServer = new WireMockServer(
                            WireMockConfiguration.options()
                                .extensions(extensions)
                                // debug logging
                                // .notifier(new ConsoleNotifier(true))
                                .port(PORT)
        );
        rules.addRules();
        wireMockServer.start();
    }

    @AfterAll
    public static void tearDown() {
        wireMockServer.shutdown();
        while (wireMockServer.isRunning()) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                // We finish the tests
            }
        }
    }

    protected interface Rules {
        void addRules();
    }

    protected static class ContentLengthSetter extends ResponseTransformer {
        @Override
        public com.github.tomakehurst.wiremock.http.Response transform(
                Request request,
                com.github.tomakehurst.wiremock.http.Response response,
                FileSource files,
                Parameters parameters) {
            String content = response.getBodyAsString();
            com.github.tomakehurst.wiremock.http.Response.Builder builder =
                    com.github.tomakehurst.wiremock.http.Response.response();
            if (content != null && content.length() != 0) {
                    builder = builder
                            .body(content)
                            .headers(response.getHeaders().plus(
                                    HttpHeader.httpHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length()))
                            ));
            } else {
                builder = builder.headers(response.getHeaders());
            }
            return builder.status(response.getStatus()).build();
        }

        @Override
        public String getName() {
            return "content-length-transformer";
        }
    }

    /**
     * Usable when the method contains an operation that needs to be executed everytime.
     * The stub is cached by default.
     */
    static class UncachedResponseMethodExecutor extends ResponseTransformer {

        private final Supplier<jakarta.ws.rs.core.Response> methodSupplier;

        UncachedResponseMethodExecutor(Supplier<jakarta.ws.rs.core.Response> methodSupplier) {
            this.methodSupplier = methodSupplier;
        }

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            jakarta.ws.rs.core.Response original = methodSupplier.get();
            com.github.tomakehurst.wiremock.http.Response.Builder builder =
                    com.github.tomakehurst.wiremock.http.Response.response()
                            .status(original.getStatus());
            if (original.hasEntity()) {
                builder = builder.body(String.valueOf(original.getEntity()));
            }

            com.github.tomakehurst.wiremock.http.HttpHeaders newHeaders =
                    com.github.tomakehurst.wiremock.http.HttpHeaders.noHeaders();
            for (Map.Entry<String, List<String>> entry : original.getStringHeaders().entrySet()) {
                if (jakarta.ws.rs.core.HttpHeaders.LOCATION.equals(entry.getKey())) {
                    newHeaders = newHeaders.plus(
                            HttpHeader.httpHeader(entry.getKey(), getBaseUri() + entry.getValue().get(0))
                    );
                } else {
                    newHeaders = newHeaders.plus(
                            HttpHeader.httpHeader(entry.getKey(), entry.getValue().toArray(new String[0]))
                    );
                }
            }

            builder = builder.headers(newHeaders);
            return builder.build();
        }

        @Override
        public String getName() {
            return "uncached-response-executor";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }

    /**
     * Usable when the method contains an operation that needs to be executed everytime.
     * The stub is cached by default.
     */
    static class UncachedStringMethodExecutor extends ResponseTransformer {

        private final Supplier<String> methodSupplier;

        UncachedStringMethodExecutor(Supplier<String> methodSupplier) {
            this.methodSupplier = methodSupplier;
        }

        @Override
        public Response transform(Request request, Response response, FileSource files, Parameters parameters) {
            return com.github.tomakehurst.wiremock.http.Response.response().body(methodSupplier.get()).build();
        }

        @Override
        public String getName() {
            return "uncached-string-executor";
        }

        @Override
        public boolean applyGlobally() {
            return false;
        }
    }
}
