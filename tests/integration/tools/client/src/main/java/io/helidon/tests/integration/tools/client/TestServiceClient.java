/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
package io.helidon.tests.integration.tools.client;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.json.JsonObject;
import javax.json.JsonValue;

import io.helidon.media.common.MessageBodyReadableContent;
import io.helidon.webclient.WebClient;
import io.helidon.webclient.WebClientRequestBuilder;

/**
 * Web client to access specific service of remote test application.
 * Common service access method is still available.
 */
public class TestServiceClient extends TestClient {

    private static final Logger LOGGER = Logger.getLogger(TestServiceClient.class.getName());

    private final String service;

    private TestServiceClient(final WebClient webClient, String service) {
        super(webClient);
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
    public JsonValue callServiceAndGetData(final String method, final Map<String, String> params) {
        return evaluateServiceCallResult(
                callService(
                        clientGetBuilderWithPath(service, method),
                        params));
    }

    /**
     * Call remote test service method and return its data.
     * Using service name provided to test web client builder.
     * No query parameters are passed.
     *
     * @param method remote test method name
     * @return data returned by remote test service method
     */
    public JsonValue callServiceAndGetData(final String method) {
        return callServiceAndGetData(method, (Map) null);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done.
     *
     * @param method remote test method name
     * @param params remote test method query parameters
     * @return data returned by remote service
     */
    public JsonObject callServiceAndGetRawData(final String method, final Map<String, String> params) {
        WebClientRequestBuilder rb = clientGetBuilderWithPath(service, method);
        rb.headers().add("Accept", "application/json");
        return callService(rb, params);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done. No query parameters are passed.
     *
     * @param method remote test method name
     * @return data returned by remote service
     */
    public JsonObject callServiceAndGetRawData(final String method) {
        return callServiceAndGetRawData(service, method, null);
    }

    /**
     * Call remote service method and return its raw data as JSON object.
     * No response content check is done. No query parameters are passed.
     *
     * @param method remote test method name
     * @return data returned by remote service
     */
    public String callServiceAndGetString(final String method) {
        WebClientRequestBuilder rb = clientGetBuilderWithPath(service, method);
        final MessageBodyReadableContent content = rb.submit()
                .await(1, TimeUnit.MINUTES)
                .content();
        return content.as(String.class)
                .await(1, TimeUnit.MINUTES);
    }

    /**
     * Remote test web client builder.
     */
    public static class Builder {

        private final TestClient.Builder parentBuilder;

        private final String service;

        Builder(final TestClient.Builder parentBuilder, final String service) {
            this.parentBuilder = parentBuilder;
            this.service = service;
        }

        /**
         * Set test application URL host.
         *
         * @param host test application URL host
         * @return updated builder instance
         */
        public Builder host(final String host) {
            parentBuilder.host(host);
            return this;
        }

        /**
         * Set test application URL port.
         *
         * @param port test application URL port
         * @return updated builder instance
         */
        public Builder port(final int port) {
            parentBuilder.port(port);
            return this;
        }

        /**
         * Builds test web client initialized with parameters stored in this builder.
         *
         * @return new {@link WebClient} instance
         */
        public TestServiceClient build() {
            return new TestServiceClient(parentBuilder.buildWebClient(), service);
        }

    }

}
