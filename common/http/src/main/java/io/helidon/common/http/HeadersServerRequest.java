/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.common.http;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.common.http.Http.HeaderValue;
import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;

/**
 * HTTP headers of a server request.
 */
public interface HeadersServerRequest extends Headers {
    /**
     * Header value of the non compliant {@code Accept} header sent by
     * {@link java.net.HttpURLConnection} when none is set.
     *
     * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8163921">JDK-8163921</a>
     */
    HeaderValue HUC_ACCEPT_DEFAULT = HeaderValue.create(Http.Header.ACCEPT,
                                                        "text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2");

    /**
     * Accepted types for {@link #HUC_ACCEPT_DEFAULT}.
     */
    List<HttpMediaType> HUC_ACCEPT_DEFAULT_TYPES = List.of(
            HttpMediaType.create(MediaTypes.TEXT_HTML),
            HttpMediaType.create(MediaTypes.create("image", "gif")),
            HttpMediaType.create(MediaTypes.create("image", "jpeg")),
            HttpMediaType.builder().mediaType(MediaTypes.WILDCARD)
                    .q(0.2)
                    .build());

    /**
     * Create a new instance from headers.
     *
     * @param headers headers parsed from server request
     * @return immutable instance of request headers
     */
    static HeadersServerRequest create(Headers headers) {
        return new HeadersServerRequestImpl(headers);
    }

    /**
     * Optionally returns a value of {@link Http.Header#IF_MODIFIED_SINCE} header.
     * <p>
     * Allows a 304 Not Modified to be returned if content is unchanged.
     *
     * @return Content of {@link Http.Header#IF_MODIFIED_SINCE} header.
     */
    default Optional<ZonedDateTime> ifModifiedSince() {
        if (contains(Http.Header.IF_MODIFIED_SINCE)) {
            return Optional.of(get(Http.Header.IF_MODIFIED_SINCE))
                    .map(HeaderValue::value)
                    .map(Http.DateTime::parse);
        }

        return Optional.empty();
    }

    /**
     * Optionally returns a value of {@link Http.Header#IF_UNMODIFIED_SINCE} header.
     * <p>
     * <i>Only send the response if the entity has not been modified since a specific time.</i>
     *
     * @return Content of {@link Http.Header#IF_UNMODIFIED_SINCE} header.
     */
    default Optional<ZonedDateTime> ifUnmodifiedSince() {
        if (contains(Http.Header.IF_UNMODIFIED_SINCE)) {
            return Optional.of(get(Http.Header.IF_UNMODIFIED_SINCE))
                    .map(HeaderValue::value)
                    .map(Http.DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Test if the given media type is acceptable as a response for this request.
     * A media type is accepted if the {@code Accept} header is not present in the
     * request or if it contains the provided media type.
     *
     * @param mediaType the media type to test
     * @return {@code true} if provided type is acceptable, {@code false} otherwise
     * @throws NullPointerException if the provided type is {@code null}.
     */
    default boolean isAccepted(MediaType mediaType) {
        List<HttpMediaType> accepted = acceptedTypes();
        if (accepted.isEmpty()) {
            return true;
        }
        for (HttpMediaType acceptedType : accepted) {
            if (acceptedType.test(mediaType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Optionally returns a single media type from the given media types that is the
     * best one accepted by the client.
     * Method uses content negotiation {@link io.helidon.common.http.Http.Header#ACCEPT}
     * header parameter and returns an empty value in case nothing matches.
     *
     * @param mediaTypes media type candidates, never null
     * @return an accepted media type.
     */
    default Optional<MediaType> bestAccepted(MediaType... mediaTypes) {
        if (mediaTypes.length == 0) {
            return Optional.empty();
        }
        List<HttpMediaType> accepted = acceptedTypes();
        if (accepted.isEmpty()) {
            return Optional.of(mediaTypes[0]);
        }
        for (HttpMediaType acceptedType : accepted) {
            for (MediaType mediaType : mediaTypes) {
                if (acceptedType.test(mediaType)) {
                    return Optional.of(mediaType);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Returns cookies (parsed from '{@code Cookie:}' header) based on <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
     * It parses also older formats including RFC2965 but skips parameters. Only cookie {@code name} and {@code value}
     * is returned.
     *
     * <p>Multiple cookies can be returned in a single headers and a single cookie-name can have multiple values.
     * Note that base on RFC6265 an order of cookie values has no semantics.
     *
     * @return An unmodifiable cookies represented by {@link Parameters} interface where key is a name of the cookie and
     * values are cookie values.
     */
    default Parameters cookies() {
        if (contains(Http.Header.COOKIE)) {
            return CookieParser.parse(get(Http.Header.COOKIE));
        } else {
            return CookieParser.empty();
        }
    }

    /**
     * Optionally returns acceptedTypes version in time ({@link  io.helidon.common.http.Http.Header#ACCEPT_DATETIME} header).
     *
     * @return Acceptable version in time.
     */
    default Optional<ZonedDateTime> acceptDatetime() {
        if (contains(Http.Header.ACCEPT_DATETIME)) {
            return Optional.of(get(Http.Header.ACCEPT_DATETIME))
                    .map(HeaderValue::value)
                    .map(Http.DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Optionally returns request date ({@link io.helidon.common.http.Http.Header#DATE} header).
     *
     * @return Request date.
     */
    default Optional<ZonedDateTime> date() {
        if (contains(Http.Header.DATE)) {
            return Optional.of(get(Http.Header.DATE))
                    .map(HeaderValue::value)
                    .map(Http.DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Optionally returns the address of the previous web page (header {@link io.helidon.common.http.Http.Header#REFERER}) from which a link
     * to the currently requested page was followed.
     * <p>
     * <i>The word {@code referrer} has been misspelled in the RFC as well as in most implementations to the point that it
     * has become standard usage and is considered correct terminology</i>
     *
     * @return Referrers URI
     */
    default Optional<URI> referer() {
        if (contains(Http.Header.REFERER)) {
            return Optional.of(get(Http.Header.REFERER))
                    .map(HeaderValue::value)
                    .map(URI::create);
        }
        return Optional.empty();
    }
}
