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

package io.helidon.nima.webclient.api;

import java.util.concurrent.CompletableFuture;

import io.helidon.builder.api.Prototype;
import io.helidon.common.buffers.DataReader;
import io.helidon.common.http.ClientResponseHeaders;
import io.helidon.common.http.Http;

/**
 * Response which is created upon receiving of server response.
 */
@Prototype.Blueprint
interface WebClientServiceResponseBlueprint {

    /**
     * Received response headers.
     *
     * @return immutable response headers
     */
    ClientResponseHeaders headers();

    /**
     * Status of the response.
     *
     * @return response status
     */
    Http.Status status();

    /**
     * Data reader to obtain response bytes.
     *
     * @return data reader
     */
    DataReader reader();

    /**
     * Client connection that was used to handle this request.
     * This connection will be closed/released once the entity is fully read, depending on keep alive configuration.
     *
     * @return connection
     */
    ClientConnection connection();

    /**
     * Completable future to be completed by the client response when the entity is fully read.
     *
     * @return completable future to be finished by the client response
     */
    CompletableFuture<WebClientServiceResponse> whenComplete();

    /**
     * The service request used to invoke the final call.
     *
     * @return service request
     */
    WebClientServiceRequest serviceRequest();
}
