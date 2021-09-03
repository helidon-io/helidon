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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

import io.netty.handler.codec.http.cookie.ClientCookieEncoder;
import io.netty.handler.codec.http.cookie.DefaultCookie;

/**
 * Client request header implementation.
 */
class WebClientRequestHeadersImpl implements WebClientRequestHeaders {

    private static final DateTimeFormatter FORMATTER = Http.DateTime.RFC_1123_DATE_TIME.withZone(ZoneId.of("GMT"));

    private final Map<String, List<String>> headers = new HashMap<>();

    WebClientRequestHeadersImpl() {
    }

    WebClientRequestHeadersImpl(WebClientRequestHeaders headers) {
        this.headers.putAll(headers.toMap());
    }

    @Override
    public WebClientRequestHeaders unsetHeader(String name) {
        headers.remove(name);
        return this;
    }

    @Override
    public WebClientRequestHeaders addCookie(String name, String value) {
        add(Http.Header.COOKIE, ClientCookieEncoder.STRICT.encode(new DefaultCookie(name, value)));
        return this;
    }

    @Override
    public WebClientRequestHeaders contentType(MediaType contentType) {
        put(Http.Header.CONTENT_TYPE, contentType.toString());
        return this;
    }

    @Override
    public WebClientRequestHeaders contentLength(long length) {
        put(Http.Header.CONTENT_LENGTH, Long.toString(length));
        return this;
    }

    @Override
    public WebClientRequestHeaders addAccept(MediaType mediaType) {
        add(Http.Header.ACCEPT, mediaType.toString());
        return this;
    }

    @Override
    public WebClientRequestHeaders ifModifiedSince(ZonedDateTime time) {
        put(Http.Header.IF_MODIFIED_SINCE, time.format(FORMATTER));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifUnmodifiedSince(ZonedDateTime time) {
        put(Http.Header.IF_UNMODIFIED_SINCE, time.format(FORMATTER));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifNoneMatch(String... etags) {
        put(Http.Header.IF_NONE_MATCH, processEtags(etags));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifMatch(String... etags) {
        put(Http.Header.IF_MATCH, processEtags(etags));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifRange(ZonedDateTime time) {
        put(Http.Header.IF_RANGE, time.format(FORMATTER));
        return this;
    }

    @Override
    public WebClientRequestHeaders ifRange(String etag) {
        put(Http.Header.IF_RANGE, processEtags(etag));
        return this;
    }

    @Override
    public List<MediaType> acceptedTypes() {
        List<MediaType> mediaTypes = new ArrayList<>();
        headers.computeIfAbsent(Http.Header.ACCEPT, k -> new ArrayList<>())
                .forEach(s -> mediaTypes.add(MediaType.parse(s)));
        return Collections.unmodifiableList(mediaTypes);
    }

    @Override
    public MediaType contentType() {
        List<String> contentType = headers.computeIfAbsent(Http.Header.CONTENT_TYPE, k -> new ArrayList<>());
        return contentType.size() == 0 ? MediaType.WILDCARD : MediaType.parse(contentType.get(0));
    }

    @Override
    public Optional<Long> contentLength() {
        return Optional.ofNullable(headers.get(Http.Header.CONTENT_LENGTH)).map(list -> Long.parseLong(list.get(0)));
    }

    @Override
    public Optional<ZonedDateTime> ifModifiedSince() {
        return parseToDate(Http.Header.IF_MODIFIED_SINCE);
    }

    @Override
    public Optional<ZonedDateTime> ifUnmodifiedSince() {
        return parseToDate(Http.Header.IF_UNMODIFIED_SINCE);
    }

    @Override
    public List<String> ifNoneMatch() {
        return all(Http.Header.IF_NONE_MATCH)
                .stream()
                .map(this::unquoteETag)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> ifMatch() {
        return all(Http.Header.IF_MATCH)
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
    public void clear() {
        this.headers.clear();
    }

    @Override
    public Optional<String> first(String name) {
        return Optional.ofNullable(headers.get(name)).map(list -> list.get(0));
    }

    @Override
    public List<String> all(String headerName) {
        return Collections.unmodifiableList(headers.getOrDefault(headerName, new ArrayList<>()));
    }

    @Override
    public List<String> put(String key, String... values) {
        List<String> list = headers.put(key, Arrays.asList(values));
        return Collections.unmodifiableList(list == null ? new ArrayList<>() : list);
    }

    @Override
    public List<String> put(String key, Iterable<String> values) {
        List<String> list = headers.put(key, iterableToList(values));
        return Collections.unmodifiableList(list == null ? new ArrayList<>() : list);
    }

    @Override
    public List<String> putIfAbsent(String key, String... values) {
        List<String> list = headers.putIfAbsent(key, Arrays.asList(values));
        return Collections.unmodifiableList(list == null ? new ArrayList<>() : list);
    }

    @Override
    public List<String> putIfAbsent(String key, Iterable<String> values) {
        List<String> list = headers.putIfAbsent(key, iterableToList(values));
        return Collections.unmodifiableList(list == null ? new ArrayList<>() : list);
    }

    @Override
    public List<String> computeIfAbsent(String key, Function<String, Iterable<String>> values) {
        List<String> associatedHeaders = headers.get(key);
        if (associatedHeaders == null) {
            return put(key, values.apply(key));
        }
        return Collections.unmodifiableList(associatedHeaders);
    }

    @Override
    public List<String> computeSingleIfAbsent(String key, Function<String, String> value) {
        List<String> associatedHeaders = headers.get(key);
        if (associatedHeaders == null) {
            return put(key, value.apply(key));
        }
        return Collections.unmodifiableList(associatedHeaders);
    }

    @Override
    public void putAll(Parameters parameters) {
        headers.putAll(parameters.toMap());
    }

    @Override
    public void add(String key, String... values) {
        headers.computeIfAbsent(key, k -> new ArrayList<>()).addAll(Arrays.asList(values));
    }

    @Override
    public void add(String key, Iterable<String> values) {
        headers.computeIfAbsent(key, k -> new ArrayList<>()).addAll(iterableToList(values));
    }

    @Override
    public void addAll(Parameters parameters) {
        parameters.toMap().forEach(this::add);
    }

    @Override
    public List<String> remove(String key) {
        List<String> value = headers.remove(key);
        return value == null ? new ArrayList<>() : value;
    }

    @Override
    public Map<String, List<String>> toMap() {
        return Collections.unmodifiableMap(new HashMap<>(headers));
    }

    private List<String> iterableToList(Iterable<String> iterable) {
        return StreamSupport
                .stream(iterable.spliterator(), false)
                .collect(Collectors.toList());
    }

    private Optional<ZonedDateTime> parseToDate(String header) {
        return first(header).map(Http.DateTime::parse);
    }

    private Iterable<String> processEtags(String... etags) {
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
        return set;
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
