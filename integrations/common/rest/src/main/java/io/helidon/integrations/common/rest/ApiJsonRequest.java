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

import io.helidon.common.http.MediaType;

/**
 * Common base class for REST requests that have an entity.
 * This class acts as a mutable builder of request JSON object.
 *
 * Path is not a part of this request.
 *
 * @param <T> type of the request
 * @see io.helidon.integrations.common.rest.ApiRequest
 */
public abstract class ApiJsonRequest<T extends ApiJsonRequest<T>> extends ApiJsonBuilder<T> implements ApiRequest<T> {
    private final RestRequest delegate = RestRequest.builder();

    /**
     * Default constructor.
     */
    protected ApiJsonRequest() {
    }

    /**
     * Add an HTTP header.
     *
     * @param name name of the header
     * @param value header value(s)
     * @return updated request
     */
    @Override
    public T addHeader(String name, String... value) {
        delegate.addHeader(name, value);
        return me();
    }

    /**
     * Add an HTTP query parameter.
     *
     * @param name name of the parameter
     * @param value parameter value(s)
     * @return updated request
     */
    @Override
    public T addQueryParam(String name, String... value) {
        delegate.addQueryParam(name, value);
        return me();
    }

    /**
     * Returns immutable configured headers.
     *
     * @return headers configured for this request
     */
    @Override
    public Map<String, List<String>> headers() {
        return delegate.headers();
    }

    /**
     * Returns immutable configured query parameters.
     *
     * @return query parameters configured for this request
     */
    @Override
    public Map<String, List<String>> queryParams() {
        return delegate.queryParams();
    }

    @Override
    public T requestMediaType(MediaType mediaType) {
        delegate.requestMediaType(mediaType);
        return me();
    }

    @Override
    public T responseMediaType(MediaType mediaType) {
        delegate.responseMediaType(mediaType);
        return me();
    }

    @Override
    public T requestId(String requestId) {
        delegate.requestId(requestId);
        return me();
    }

    @Override
    public Optional<MediaType> requestMediaType() {
        return delegate.requestMediaType();
    }

    @Override
    public Optional<MediaType> responseMediaType() {
        return delegate.responseMediaType();
    }

    @Override
    public Optional<String> requestId() {
        return delegate.requestId();
    }
}
