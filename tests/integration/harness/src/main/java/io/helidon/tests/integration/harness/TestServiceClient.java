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

import java.util.Map;

import io.helidon.nima.webclient.http1.Http1Client;
import io.helidon.nima.webclient.http1.Http1ClientRequest;

import jakarta.json.JsonValue;

/**
 * Web client to access specific service of remote test application.
 * Common service access method is still available.
 */
public class TestServiceClient extends TestClient {

    private final String service;

    private TestServiceClient(Http1Client client, String service) {
        super(client);
        this.service = service;
    }

    /**
     * Call remote test service method and return its data.
     * Using service name provided to test web client builder.
     *
     * @param method remote test method name
     * @param params remote test method query parameters
     * @return data returned by remote test service method
     */
    public JsonValue callServiceAndGetData(String method, Map<String, String> params) {
        return evaluateServiceCallResult(callService(clientGetBuilderWithPath(service, method), params));
    }

    /**
     * Call remote test service method and return its data.
     * Using service name provided to test web client builder.
     * No query parameters are passed.
     *
     * @param method remote test method name
     * @return data returned by remote test service method
     */
    public JsonValue callServiceAndGetData(String method) {
        return callServiceAndGetData(method, (Map<String, String>) null);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done. No query parameters are passed.
     *
     * @param method remote test method name
     * @return data returned by remote service
     */
    public String callServiceAndGetString(String method) {
        Http1ClientRequest clientRequest = clientGetBuilderWithPath(service, method);
        return clientRequest.requestEntity(String.class);
    }

    /**
     * Remote test web client builder.
     */
    public static class Builder {

        private final TestClient.Builder parentBuilder;
        private final String service;

        Builder(TestClient.Builder parentBuilder, String service) {
            this.parentBuilder = parentBuilder;
            this.service = service;
        }

        /**
         * Set test application URL host.
         *
         * @param host test application URL host
         * @return updated builder instance
         */
        public Builder host(String host) {
            parentBuilder.host(host);
            return this;
        }

        /**
         * Set test application URL port.
         *
         * @param port test application URL port
         * @return updated builder instance
         */
        public Builder port(int port) {
            parentBuilder.port(port);
            return this;
        }

        /**
         * Builds test web client initialized with parameters stored in this builder.
         *
         * @return new {@link TestServiceClient} instance
         */
        public TestServiceClient build() {
            return new TestServiceClient(parentBuilder.buildWebClient(), service);
        }

    }

}
