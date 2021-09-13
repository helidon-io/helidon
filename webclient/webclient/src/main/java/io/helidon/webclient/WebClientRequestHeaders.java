/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates.
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

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Headers;
import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

/**
 * Headers that can be modified (until request is sent) for
 * outbound request.
 */
public interface WebClientRequestHeaders extends Headers {

    /**
     * Remove a header if set.
     *
     * @param name header name
     * @return updated headers instance
     */
    WebClientRequestHeaders unsetHeader(String name);

    /**
     * Add a cookie to the request.
     *
     * @param name  cookie name
     * @param value cookie value
     * @return updated headers instance
     */
    WebClientRequestHeaders addCookie(String name, String value);

    /**
     * Set a content type. This method is optional if you use
     * a writer for a specific type.
     * If the content type is explicitly defined, writer will NOT override it.
     *
     * @param contentType content type of the request
     * @return updated headers instance
     */
    WebClientRequestHeaders contentType(MediaType contentType);

    /**
     * Set a content length. This method is optional.
     * Use only when you know the exact length of entity in bytes.
     *
     * @param length content length of entity
     * @return updated headers instance
     */
    WebClientRequestHeaders contentLength(long length);

    /**
     * Add accepted {@link MediaType}. Supports quality factor and wildcards.
     * Ordered by invocation order.
     *
     * @param mediaType media type to accept, with optional quality factor
     * @return updated headers instance
     */
    WebClientRequestHeaders addAccept(MediaType mediaType);

    /**
     * Sets {@link Http.Header#IF_MODIFIED_SINCE} header to specific time.
     *
     * @param time zoned date time
     * @return updated headers instance
     */
    WebClientRequestHeaders ifModifiedSince(ZonedDateTime time);

    /**
     * Sets {@link Http.Header#IF_UNMODIFIED_SINCE} header to specific time.
     *
     * @param time zoned date time
     * @return updated headers instance
     */
    WebClientRequestHeaders ifUnmodifiedSince(ZonedDateTime time);

    /**
     * Sets {@link Http.Header#IF_NONE_MATCH} header to specific etags.
     *
     * @param etags etags
     * @return updated headers instance
     */
    WebClientRequestHeaders ifNoneMatch(String... etags);

    /**
     * Sets {@link Http.Header#IF_MATCH} header to specific etags.
     *
     * @param etags etags
     * @return updated headers instance
     */
    WebClientRequestHeaders ifMatch(String... etags);

    /**
     * Sets {@link Http.Header#IF_RANGE} header to specific time.
     *
     * @param time zoned date time
     * @return updated headers instance
     */
    WebClientRequestHeaders ifRange(ZonedDateTime time);

    /**
     * Sets {@link Http.Header#IF_RANGE} header to specific etag.
     *
     * @param etag etag
     * @return updated headers instance
     */
    WebClientRequestHeaders ifRange(String etag);

    /**
     * Returns a list of acceptedTypes ({@value io.helidon.common.http.Http.Header#ACCEPT} header) content types in quality
     * factor order.
     * Never {@code null}.
     *
     * @return A list of acceptedTypes media types.
     */
    List<MediaType> acceptedTypes();

    /**
     * Returns content type of the request.
     *
     * If there is no explicit content set, then {@link MediaType#WILDCARD} is returned.
     *
     * @return content type of the request
     */
    MediaType contentType();

    /**
     * Returns content length if known.
     *
     * @return content length
     */
    Optional<Long> contentLength();

    /**
     * Returns value of header {@value Http.Header#IF_MODIFIED_SINCE}.
     *
     * @return IF_MODIFIED_SINCE header value.
     */
    Optional<ZonedDateTime> ifModifiedSince();

    /**
     * Returns value of header {@value Http.Header#IF_UNMODIFIED_SINCE}.
     *
     * @return IF_UNMODIFIED_SINCE header value.
     */
    Optional<ZonedDateTime> ifUnmodifiedSince();

    /**
     * Returns value of header {@value Http.Header#IF_NONE_MATCH}.
     *
     * Empty {@link List} is returned if this header is not set.
     *
     * @return A list of etags.
     */
    List<String> ifNoneMatch();

    /**
     * Returns value of header {@value Http.Header#IF_MATCH}.
     *
     * Empty {@link List} is returned if this header is not set.
     *
     * @return A list of etags.
     */
    List<String> ifMatch();

    /**
     * Returns value of header {@value Http.Header#IF_RANGE} as a {@link ZonedDateTime}.
     *
     * @return formatted header IF_RANGE as ZonedDateTime
     */
    Optional<ZonedDateTime> ifRangeDate();

    /**
     * Returns value of header {@value Http.Header#IF_RANGE} as a {@link String}.
     *
     * @return formatted header IF_RANGE as String
     */
    Optional<String> ifRangeString();

    /**
     * Clears all currently set headers.
     */
    void clear();

    @Override
    WebClientRequestHeaders putAll(Parameters parameters);

    @Override
    WebClientRequestHeaders add(String key, String... values);

    @Override
    WebClientRequestHeaders add(String key, Iterable<String> values);

    @Override
    WebClientRequestHeaders addAll(Parameters parameters);
}
