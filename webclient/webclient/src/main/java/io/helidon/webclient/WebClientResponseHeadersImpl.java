/*
 * Copyright (c) 2020, 2024 Oracle and/or its affiliates.
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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.ReadOnlyHeaders;
import io.helidon.common.http.SetCookie;

/**
 * Implementation of {@link WebClientResponseHeaders}.
 */
class WebClientResponseHeadersImpl extends ReadOnlyHeaders implements WebClientResponseHeaders {

    private final boolean mediaTypeParserRelaxed;

    private WebClientResponseHeadersImpl(Map<String, List<String>> headers, boolean mediaTypeParserRelaxed) {
        super(headers);
        this.mediaTypeParserRelaxed = mediaTypeParserRelaxed;
    }

    /**
     * Creates {@link WebClientResponseHeaders} instance which contains data from {@link Map}.
     *
     * @param headers response headers in map
     * @param mediaTypeParserRelaxed whether relaxed media type parsing mode should be used
     * @return response headers instance
     */
    protected static WebClientResponseHeadersImpl create(Map<String, List<String>> headers, boolean mediaTypeParserRelaxed) {
        return new WebClientResponseHeadersImpl(headers, mediaTypeParserRelaxed);
    }

    /**
     * Creates {@link WebClientResponseHeaders} instance which contains data from {@link Map}.
     *
     * @param headers response headers in map
     * @return response headers instance
     */
    protected static WebClientResponseHeadersImpl create(Map<String, List<String>> headers) {
        return new WebClientResponseHeadersImpl(headers, false);
    }

    @Override
    public List<SetCookie> setCookies() {
        return all(Http.Header.SET_COOKIE)
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
    public Optional<MediaType> contentType() {
        return first(Http.Header.CONTENT_TYPE)
                .map(mediaTypeParserRelaxed
                             ? MediaType::parseRelaxed
                             : MediaType::parse);
    }

    @Override
    public Optional<String> etag() {
        return first(Http.Header.ETAG).map(this::unquoteETag);
    }

    @Override
    public Optional<Long> contentLength() {
        return first(Http.Header.CONTENT_LENGTH)
                .map(Long::parseLong)
                .or(Optional::empty);
    }

    @Override
    public List<String> transferEncoding() {
        return all(Http.Header.TRANSFER_ENCODING);
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
