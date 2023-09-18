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

package io.helidon.webclient.api;

import java.io.InputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.helidon.builder.api.Prototype;
import io.helidon.http.ClientResponseHeaders;
import io.helidon.http.ClientResponseTrailers;
import io.helidon.http.Status;

/**
 * Response which is created upon receiving of server response.
 */
@Prototype.Blueprint(decorator = WebClientServiceResponseDecorator.class)
interface WebClientServiceResponseBlueprint {

    /**
     * Received response headers.
     *
     * @return immutable response headers
     */
    ClientResponseHeaders headers();

    /**
     * Received response trailer headers.
     *
     * @return immutable response trailer headers
     */
    CompletableFuture<ClientResponseTrailers> trailers();

    /**
     * Status of the response.
     *
     * @return response status
     */
    Status status();

    /**
     * Input stream to get data of the entity. This allows decorating the entity (such as decryption).
     * The status, headers are always already read, and the input stream will not provide transfer encoded bytes
     * (e.g. the bytes in the input stream are the entity bytes, regardless of how it is encoded over HTTP).
     *
     * @return entity input stream, or empty, if there is no entity
     */
    Optional<InputStream> inputStream();

    /**
     * Client connection/stream that was used to handle this request.
     * This resource will be closed/released once the entity is fully read, depending on keep alive configuration.
     *
     * @return connection resource
     */
    ReleasableResource connection();

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
