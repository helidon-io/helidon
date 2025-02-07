/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.http;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import io.helidon.common.media.type.MediaType;

import static io.helidon.http.HeaderNames.EXPIRES;
import static io.helidon.http.HeaderNames.LAST_MODIFIED;
import static io.helidon.http.HeaderNames.LOCATION;

/**
 * Mutable headers of a server response.
 */
public interface ServerResponseHeaders extends ClientResponseHeaders,
                                               WritableHeaders<ServerResponseHeaders> {

    /**
     * Create a new instance of mutable server response headers.
     *
     * @return new server response headers
     */
    static ServerResponseHeaders create() {
        return new ServerResponseHeadersImpl();
    }

    /**
     * Create a new instance of mutable server response headers.
     *
     * @param existing headers to add to these response headers
     * @return new server response headers
     */
    static ServerResponseHeaders create(Headers existing) {
        return new ServerResponseHeadersImpl(existing);
    }

    /**
     * Adds one or more acceptedTypes path document formats
     * (header {@link HeaderNames#ACCEPT_PATCH}).
     *
     * @param acceptableMediaTypes media types to add.
     * @return this instance
     */
    default ServerResponseHeaders addAcceptPatches(MediaType... acceptableMediaTypes) {
        String[] values = new String[acceptableMediaTypes.length];
        for (int i = 0; i < acceptableMediaTypes.length; i++) {
            MediaType acceptableMediaType = acceptableMediaTypes[i];
            values[i] = acceptableMediaType.text();
        }
        return add(HeaderValues.create(HeaderNames.ACCEPT_PATCH,
                                       values));
    }

    /**
     * Adds {@code Set-Cookie} header specified in <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
     *
     * @param cookie a cookie definition
     * @return this instance
     */
    ServerResponseHeaders addCookie(SetCookie cookie);

    /**
     * Adds {@code Set-Cookie} header based on <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> with {@code Max-Age}
     * parameter.
     *
     * @param name   name of the cookie
     * @param value  value of the cookie
     * @param maxAge {@code Max-Age} cookie parameter
     * @return this instance
     */
    default ServerResponseHeaders addCookie(String name, String value, Duration maxAge) {
        return addCookie(SetCookie.builder(name, value)
                                 .maxAge(maxAge)
                                 .build());
    }

    /**
     * Adds {@code Set-Cookie} header based on <a href="https://tools.ietf.org/html/rfc2616">RFC2616</a>.
     *
     * @param name  name of the cookie
     * @param value value of the cookie
     * @return this instance
     */
    default ServerResponseHeaders addCookie(String name, String value) {
        return addCookie(SetCookie.create(name, value));
    }

    /**
     * Clears a cookie by adding a {@code Set-Cookie} header with an expiration date in the past.
     * It is recommended to use {@link #clearCookie(SetCookie)} instead for better handling
     * of Path and Domain.
     *
     * @param name name of the cookie.
     * @return this instance
     */
    ServerResponseHeaders clearCookie(String name);

    /**
     * Clears a cookie by adding a {@code Set-Cookie} header with an expiration date in the past.
     *
     * @param setCookie the cookie.
     * @return this instance
     */
    default ServerResponseHeaders clearCookie(SetCookie setCookie) {
        return clearCookie(setCookie.name());
    }

    /**
     * Sets the value of {@link HeaderNames#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object
     *
     * @param modified Last modified date/time.
     * @return this instance
     */
    default ServerResponseHeaders lastModified(Instant modified) {
        ZonedDateTime dt = ZonedDateTime.ofInstant(modified, ZoneId.systemDefault());
        return set(HeaderValues.create(LAST_MODIFIED, true, false, dt.format(DateTime.RFC_1123_DATE_TIME)));
    }

    /**
     * Sets the value of {@link HeaderNames#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object
     *
     * @param modified Last modified date/time.
     * @return this instance
     */
    default ServerResponseHeaders lastModified(ZonedDateTime modified) {
        return set(HeaderValues.create(LAST_MODIFIED, true, false, modified.format(DateTime.RFC_1123_DATE_TIME)));
    }

    /**
     * Sets the value of {@link HeaderNames#LOCATION} header.
     * <p>
     * Used in redirection, or when a new resource has been created.
     *
     * @param location Location header value.
     * @return updated headers
     */
    default ServerResponseHeaders location(URI location) {
        return set(HeaderValues.create(LOCATION, true, false, location.toASCIIString()));
    }

    /**
     * Sets the value of {@link HeaderNames#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @return updated headers
     */
    default ServerResponseHeaders expires(ZonedDateTime dateTime) {
        return set(HeaderValues.create(EXPIRES, dateTime.format(DateTime.RFC_1123_DATE_TIME)));
    }

    /**
     * Sets the value of {@link HeaderNames#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @return updated headers
     */
    default ServerResponseHeaders expires(Instant dateTime) {
        return set(HeaderValues.create(EXPIRES, ZonedDateTime.ofInstant(dateTime, ZoneId.systemDefault())
                .format(DateTime.RFC_1123_DATE_TIME)));
    }
}
