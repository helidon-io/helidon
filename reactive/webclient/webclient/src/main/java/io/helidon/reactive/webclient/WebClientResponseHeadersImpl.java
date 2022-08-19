/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates.
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
package io.helidon.reactive.webclient;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.SetCookie;

/**
 * Implementation of {@link WebClientResponseHeaders}.
 */
class WebClientResponseHeadersImpl implements WebClientResponseHeaders {
    private final Headers headers;

    private WebClientResponseHeadersImpl(Headers headers) {
        this.headers = headers;
    }

    /**
     * Creates {@link WebClientResponseHeaders} instance which contains data from {@link Map}.
     *
     * @param headers response headers in map
     * @return response headers instance
     */
    static WebClientResponseHeadersImpl create(Headers headers) {
        return new WebClientResponseHeadersImpl(headers);
    }

    @Override
    public List<SetCookie> setCookies() {
        return all(Http.Header.SET_COOKIE, List::of)
                .stream()
                .map(SetCookie::parse)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<URI> location() {
        return first(Http.Header.LOCATION).map(URI::create);
    }

    @Override
    public Optional<ZonedDateTime> lastModified() {
        return first(Http.Header.LAST_MODIFIED).map(Http.DateTime::parse);
    }

    @Override
    public Optional<ZonedDateTime> expires() {
        return first(Http.Header.EXPIRES).map(Http.DateTime::parse);
    }

    @Override
    public Optional<ZonedDateTime> date() {
        return first(Http.Header.DATE).map(Http.DateTime::parse);
    }

    @Override
    public Optional<HttpMediaType> contentType() {
        return first(Http.Header.CONTENT_TYPE).map(HttpMediaType::create);
    }

    @Override
    public Optional<String> etag() {
        return first(Http.Header.ETAG).map(this::unquoteETag);
    }

    @Override
    public OptionalLong contentLength() {
        return headers.contentLength();
    }

    @Override
    public List<String> transferEncoding() {
        return all(Http.Header.TRANSFER_ENCODING, List::of);
    }

    @Override
    public List<String> all(Http.HeaderName name, Supplier<List<String>> defaultSupplier) {
        return headers.all(name, defaultSupplier);
    }

    @Override
    public boolean contains(Http.HeaderName name) {
        return headers.contains(name);
    }

    @Override
    public boolean contains(Http.HeaderValue value) {
        return headers.contains(value);
    }

    @Override
    public Http.HeaderValue get(Http.HeaderName name) {
        return headers.get(name);
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public Iterator<Http.HeaderValue> iterator() {
        return headers.iterator();
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return List.of();
    }

    private String unquoteETag(String etag) {
        if (etag == null || etag.isEmpty()) {
            return etag;
        }
        if (etag.startsWith("W/") || etag.startsWith("w/")) {
            etag = etag.substring(2);
        }
        if (etag.startsWith("\"") && etag.endsWith("\"")) {
            etag = etag.substring(1, etag.length() - 1);
        }
        return etag;
    }

}
