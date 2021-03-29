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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.http.MediaType;

/**
 * Common base class for REST requests.
 * This class acts as a mutable builder without a build method, as the intended use is to pass it
 * to a {@code io.helidon.integrations.common.rest.RestApi}, not to send it around for parallel processing.
 *
 * Path is not a part of this request.
 *
 * @param <T> type of the request
 */
public abstract class ApiRestRequest<T extends ApiRequest<T>> implements ApiRequest<T> {
    private final Map<String, List<String>> queryParams = new HashMap<>();
    private final Map<String, List<String>> headers = new HashMap<>();
    private MediaType requestMediaType;
    private MediaType responseMediaType;
    private String requestId;

    /**
     * Default constructor.
     */
    protected ApiRestRequest() {
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
        headers.put(name, Arrays.asList(value));
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
        queryParams.put(name, Arrays.asList(value));
        return me();
    }

    @Override
    public T requestMediaType(MediaType mediaType) {
        this.requestMediaType = mediaType;
        return me();
    }

    @Override
    public T responseMediaType(MediaType mediaType) {
        this.responseMediaType = mediaType;
        return me();
    }

    @Override
    public T requestId(String requestId) {
        this.requestId = requestId;
        return me();
    }

    @Override
    public Map<String, List<String>> headers() {
        return Map.copyOf(headers);
    }

    @Override
    public Map<String, List<String>> queryParams() {
        return Map.copyOf(queryParams);
    }

    @Override
    public Optional<MediaType> requestMediaType() {
        return Optional.ofNullable(requestMediaType);
    }

    @Override
    public Optional<MediaType> responseMediaType() {
        return Optional.ofNullable(responseMediaType);
    }

    @Override
    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    /**
     * Can be returned by subclasses that can be subclassed again.
     *
     * @return this instance as a subclass type
     */
    @SuppressWarnings("unchecked")
    protected T me() {
        return (T) this;
    }
}
