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

package io.helidon.integrations.common.rest;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;

import io.helidon.common.http.MediaType;

/**
 * Common base class for REST requests.
 * This class acts as a mutable builder.
 *
 * Path is not a part of this request.
 *
 * @param <T> type of the request
 */
public interface ApiRequest<T extends ApiRequest<T>> {
    /**
     * Add an HTTP header.
     *
     * @param name name of the header
     * @param value header value(s)
     * @return updated request
     */
    T addHeader(String name, String... value);

    /**
     * Add an HTTP query parameter.
     *
     * @param name name of the parameter
     * @param value parameter value(s)
     * @return updated request
     */
    T addQueryParam(String name, String... value);

    /**
     * The media type header, defaults to {@link io.helidon.common.http.MediaType#APPLICATION_JSON} when JSON entity is present,
     * to {@link io.helidon.common.http.MediaType#APPLICATION_OCTET_STREAM} for publisher base requests, empty otherwise.
     *
     * @param mediaType media type of request
     * @return updated request
     */
    T requestMediaType(MediaType mediaType);

    /**
     * The accept header, defaults to {@link io.helidon.common.http.MediaType#APPLICATION_JSON} for most requests, except
     * for requests that return publisher, which default to {@link io.helidon.common.http.MediaType#APPLICATION_OCTET_STREAM}.
     *
     * @param mediaType accepted media type
     * @return updated request
     */
    T responseMediaType(MediaType mediaType);

    /**
     * Configure request ID for logging (and possibly to send over the network).
     *
     * @param requestId request id
     * @return updated request
     */
    T requestId(String requestId);

    /**
     * Returns immutable configured headers.
     *
     * @return headers configured for this request
     */
    Map<String, List<String>> headers();

    /**
     * Returns immutable configured query parameters.
     *
     * @return query parameters configured for this request
     */
    Map<String, List<String>> queryParams();

    /**
     * Return the JSON object used for POST and PUT requests (and other methods if needed).
     * The default implementation returns an
     * empty optional that can be used for GET, HEAD, DELETE methods (and other methods without an entity).
     *
     * @param factory builder factory to construct JSON object
     * @return JSON if available on this request
     */
    default Optional<JsonObject> toJson(JsonBuilderFactory factory) {
        return Optional.empty();
    }

    /**
     * Request media type.
     *
     * @return media type if configured
     * @see #requestMediaType(io.helidon.common.http.MediaType)
     */
    Optional<MediaType> requestMediaType();

    /**
     * Response media type.
     *
     * @return media type if configured
     * @see #responseMediaType(io.helidon.common.http.MediaType)
     */
    Optional<MediaType> responseMediaType();

    /**
     * Configured request ID.
     *
     * @return request ID if configured
     */
    Optional<String> requestId();
}
