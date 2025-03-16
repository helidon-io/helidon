/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.declarative.tests.http;

import io.helidon.http.Http;
import io.helidon.webclient.api.RestClient;

/**
 * API for typed client for the Greet endpoint HTTP API.
 */
@RestClient.Endpoint
public interface GreetEndpointClient extends GreetEndpointApi {
    /**
     * Return a worldly greeting message.
     *
     * @return greeting
     */
    @Http.GET
    @Http.Produces("text/plain")
    @RestClient.ComputedHeader(name = "X-Computed", producerClass = ClientHeaderProducer.class)
    String getDefaultMessageHandlerPlain();
}
