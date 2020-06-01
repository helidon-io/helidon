/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.webclient;

import java.net.URI;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.media.common.MessageBodyReadableContent;

/**
 * Response from a server that was created for our request.
 * An instance is created only if we receive a real response
 * over HTTP. For other cases (e.g. a timeout) the flow ends
 * exceptionally.
 */
public interface WebClientResponse {
    /**
     * Status of this response.
     *
     * @return HTTP status
     */
    Http.ResponseStatus status();

    /**
     * Content to access entity.
     * The content is never null, though it may be empty (e.g.
     * for HTTP PUT method, we do not get any entity back).
     *
     * @return content
     */
    MessageBodyReadableContent content();

    /**
     * Headers of the HTTP response.
     *
     * @return headers that were present in the response from server
     */
    WebClientResponseHeaders headers();

    /**
     * Http version of this response.
     *
     * @return http version
     */
    Http.Version version();

    /**
     * URI of the last request. (after redirection)
     *
     * @return last URI
     */
    URI lastEndpointURI();

    /**
     * Asynchronous close of the response.
     *
     * @return single of the closing process
     */
    Single<Void> close();
}
