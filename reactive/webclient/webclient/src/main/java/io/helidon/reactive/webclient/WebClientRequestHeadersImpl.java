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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.http.ClientRequestHeaders;
import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.http.HttpMediaType;
import io.helidon.common.http.WritableHeaders;
import io.helidon.common.media.type.MediaType;

import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;

/**
 * Client request header implementation.
 */
class WebClientRequestHeadersImpl implements WebClientRequestHeaders {

    private static final DateTimeFormatter FORMATTER = Http.DateTime.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    private final ClientRequestHeaders headers = ClientRequestHeaders.create(WritableHeaders.create());

    WebClientRequestHeadersImpl() {
    }

    WebClientRequestHeadersImpl(WebClientRequestHeaders headers) {
        for (HeaderValue header : headers) {
            this.headers.add(header);
        }
    }

    @Override
    public WebClientRequestHeaders unsetHeader(Http.HeaderName name) {
        headers.remove(name);
        return this;
    }

    @Override
    public WebClientRequestHeaders addCookie(String name, String value) {
        headers.add(HeaderValue.create(Http.Header.COOKIE, ClientCookieEncoder.STRICT.encode(new DefaultCookie(name, value))));
        return this;
    }

    @Override
    public WebClientRequestHeaders contentType(MediaType contentType) {
        headers.contentType(contentType);
        return this;
    }

    @Override
    public WebClientRequestHeaders contentLength(long length) {
        headers.contentLength(length);
        return this;
    }

    @Override
    public WebClientRequestHeaders addAccept(HttpMediaType mediaType) {
        headers.add(HeaderValue.create(Http.Header.ACCEPT, mediaType.text()));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifModifiedSince(ZonedDateTime time) {
        headers.set(HeaderValue.create(Http.Header.IF_MODIFIED_SINCE,
                                       true,
                                       false,
                                       time.format(FORMATTER)));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifUnmodifiedSince(ZonedDateTime time) {
        headers.set(HeaderValue.create(Http.Header.IF_UNMODIFIED_SINCE,
                                       true,
                                       false,
                                       time.format(FORMATTER)));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifNoneMatch(String... etags) {
        headers.set(HeaderValue.create(Http.Header.IF_NONE_MATCH, processEtags(etags)));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifMatch(String... etags) {
        headers.set(HeaderValue.create(Http.Header.IF_MATCH, processEtags(etags)));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifRange(ZonedDateTime time) {
        headers.set(HeaderValue.create(Http.Header.IF_RANGE, time.format(FORMATTER)));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifRange(String etag) {
        headers.set(HeaderValue.create(Http.Header.IF_RANGE, processEtags(etag)));
        return this;
    }

    @Override
    public List<HttpMediaType> acceptedTypes() {
        return headers.acceptedTypes();
    }

    @Override
    public Optional<HttpMediaType> contentType() {
        return headers.contentType();
    }

    @Override
    public OptionalLong contentLength() {
        return headers.contentLength();
    }

    @Override
    public Optional<ZonedDateTime> ifModifiedSince() {
        return headers.ifModifiedSince();
    }

    @Override
    public Optional<ZonedDateTime> ifUnmodifiedSince() {
        return headers.ifUnmodifiedSince();
    }


    @Override
    public List<String> ifNoneMatch() {
        return all(Http.Header.IF_NONE_MATCH, List::of)
                .stream()
                .map(this::unquoteETag)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> ifMatch() {
        return all(Http.Header.IF_MATCH, List::of)
                .stream()
                .map(this::unquoteETag)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ZonedDateTime> ifRangeDate() {
        return parseToDate(Http.Header.IF_RANGE);
    }

    @Override
    public Optional<String> ifRangeString() {
        return first(Http.Header.IF_RANGE).map(this::unquoteETag);
    }

    @Override
    public WebClientRequestHeaders clear() {
        this.headers.clear();
        return this;
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
    public boolean contains(HeaderValue value) {
        return headers.contains(value);
    }

    @Override
    public HeaderValue get(Http.HeaderName name) {
        return headers.get(name);
    }

    @Override
    public int size() {
        return headers.size();
    }

    @Override
    public WebClientRequestHeaders setIfAbsent(HeaderValue header) {
        headers.setIfAbsent(header);
        return this;
    }

    @Override
    public WebClientRequestHeaders add(HeaderValue header) {
        headers.add(header);
        return this;
    }

    @Override
    public WebClientRequestHeaders remove(Http.HeaderName name) {
        headers.remove(name);
        return this;
    }

    @Override
    public WebClientRequestHeaders remove(Http.HeaderName name, Consumer<HeaderValue> removedConsumer) {
        headers.remove(name, removedConsumer);
        return this;
    }

    @Override
    public WebClientRequestHeaders set(HeaderValue header) {
        headers.set(header);
        return this;
    }

    @Override
    public WebClientRequestHeaders contentType(HttpMediaType contentType) {
        headers.contentType(contentType);
        return this;
    }

    @Override
    public WebClientRequestHeaders putAll(Headers headers) {
        for (HeaderValue header : headers) {
            this.headers.set(header);
        }
        return this;
    }

    @Override
    public Iterator<HeaderValue> iterator() {
        return headers.iterator();
    }

    public Optional<String> first(Http.HeaderName name) {
        if (headers.contains(name)) {
            return Optional.of(headers.get(name).value());
        }
        return Optional.empty();
    }

    @Override
    public WebClientRequestHeaders add(String key, String... values) {
        headers.add(HeaderValue.create(Http.Header.create(key), values));
        return this;
    }

    @Override
    public WebClientRequestHeaders addAll(Headers headers) {
        for (HeaderValue header : headers) {
            this.headers.add(header);
        }
        return this;
    }


    private Optional<ZonedDateTime> parseToDate(Http.HeaderName header) {
        return first(header).map(Http.DateTime::parse);
    }

    private List<String> processEtags(String... etags) {
        Set<String> set = new HashSet<>();
        if (etags.length > 0) {
            if (etags.length == 1 && etags[0].equals("*")) {
                set.add(etags[0]);
            } else {
                for (String etag : etags) {
                    set.add('"' + etag + '"');
                }
            }
        }
        return new ArrayList<>(set);
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
