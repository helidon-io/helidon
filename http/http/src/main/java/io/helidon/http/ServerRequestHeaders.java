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
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import io.helidon.common.media.type.MediaType;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.common.parameters.Parameters;
import io.helidon.service.registry.Service;

/**
 * HTTP headers of a server request.
 */
@Service.Describe(Service.PerRequest.class)
public interface ServerRequestHeaders extends Headers {
    /**
     * Header value of the non compliant {@code Accept} header sent by
     * {@link java.net.HttpURLConnection} when none is set.
     *
     * @see <a href="https://bugs.openjdk.java.net/browse/JDK-8163921">JDK-8163921</a>
     */
    Header HUC_ACCEPT_DEFAULT = HeaderValues.create(HeaderNames.ACCEPT,
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
    static ServerRequestHeaders create(Headers headers) {
        return new ServerRequestHeadersImpl(headers);
    }

    /**
     * Create new empty server request headers.
     *
     * @return empty headers
     */
    static ServerRequestHeaders create() {
        return new ServerRequestHeadersImpl(WritableHeaders.create());
    }

    /**
     * Optionally returns a value of {@link HeaderNames#IF_MODIFIED_SINCE} header.
     * <p>
     * Allows a 304 Not Modified to be returned if content is unchanged.
     *
     * @return Content of {@link HeaderNames#IF_MODIFIED_SINCE} header.
     */
    default Optional<ZonedDateTime> ifModifiedSince() {
        if (contains(HeaderNames.IF_MODIFIED_SINCE)) {
            return Optional.of(get(HeaderNames.IF_MODIFIED_SINCE))
                    .map(Header::get)
                    .map(DateTime::parse);
        }

        return Optional.empty();
    }

    /**
     * Optionally returns a value of {@link HeaderNames#IF_UNMODIFIED_SINCE} header.
     * <p>
     * <i>Only send the response if the entity has not been modified since a specific time.</i>
     *
     * @return Content of {@link HeaderNames#IF_UNMODIFIED_SINCE} header.
     */
    default Optional<ZonedDateTime> ifUnmodifiedSince() {
        if (contains(HeaderNames.IF_UNMODIFIED_SINCE)) {
            return Optional.of(get(HeaderNames.IF_UNMODIFIED_SINCE))
                    .map(Header::get)
                    .map(DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Test if the given media type is acceptable as a response for this request.
     * A media type is accepted if the {@code Accept} header is not present in the
     * request or if it contains the provided media type.
     *
     * @param mediaTypes the media type(s) to test
     * @return {@code true} if provided type is acceptable, {@code false} otherwise
     * @throws NullPointerException if the provided type is {@code null}.
     */
    @Override
    default boolean isAccepted(MediaType... mediaTypes) {
        List<HttpMediaType> accepted = acceptedTypes();
        if (accepted.isEmpty()) {
            return true;
        }
        for (HttpMediaType acceptedType : accepted) {
            for (MediaType type : mediaTypes) {
                if (acceptedType.test(type)) {
                    return true;
                }
            }

        }
        return false;
    }

    /**
     * Optionally returns a single media type from the given media types that is the
     * best one accepted by the client.
     * Method uses content negotiation {@link HeaderNames#ACCEPT}
     * header parameter and returns an empty value in case nothing matches.
     *
     * @param mediaTypes media type candidates, never null
     * @return an accepted media type.
     */
    default Optional<MediaType> bestAccepted(MediaType... mediaTypes) {
        if (mediaTypes.length == 0) {
            return Optional.empty();
        }
        try {
            List<HttpMediaType> accepted = acceptedTypes();
            if (accepted.isEmpty()) {
                return Optional.of(mediaTypes[0]);
            }
            double best = 0;
            MediaType result = null;
            for (MediaType mt : mediaTypes) {
                for (HttpMediaType acc : accepted) {
                    double q = acc.qualityFactor();
                    if (q > best && acc.test(mt)) {
                        if (q == 1) {
                            return Optional.of(mt);
                        } else {
                            best = q;
                            result = mt;
                        }
                    }
                }
            }
            return Optional.ofNullable(result);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unable to parse Accept header", e);
        }
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
        if (contains(HeaderNames.COOKIE)) {
            return CookieParser.parse(get(HeaderNames.COOKIE));
        } else {
            return CookieParser.empty();
        }
    }

    /**
     * Optionally returns acceptedTypes version in time ({@link  HeaderNames#ACCEPT_DATETIME} header).
     *
     * @return Acceptable version in time.
     */
    default Optional<ZonedDateTime> acceptDatetime() {
        if (contains(HeaderNames.ACCEPT_DATETIME)) {
            return Optional.of(get(HeaderNames.ACCEPT_DATETIME))
                    .map(Header::get)
                    .map(DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Optionally returns request date ({@link HeaderNames#DATE} header).
     *
     * @return Request date.
     */
    default Optional<ZonedDateTime> date() {
        if (contains(HeaderNames.DATE)) {
            return Optional.of(get(HeaderNames.DATE))
                    .map(Header::get)
                    .map(DateTime::parse);
        }
        return Optional.empty();
    }

    /**
     * Optionally returns the address of the previous web page (header {@link HeaderNames#REFERER}) from which a link
     * to the currently requested page was followed.
     * <p>
     * <i>The word {@code referrer} has been misspelled in the RFC as well as in most implementations to the point that it
     * has become standard usage and is considered correct terminology</i>
     *
     * @return Referrers URI
     */
    default Optional<URI> referer() {
        if (contains(HeaderNames.REFERER)) {
            return Optional.of(get(HeaderNames.REFERER))
                    .map(Header::get)
                    .map(URI::create);
        }
        return Optional.empty();
    }

    /**
     * Check if the content type provided over the network matches one of the content types.
     * If any of the media types is {@link io.helidon.common.media.type.MediaTypes#WILDCARD}, this method always returns
     * {@code true}.
     * If {@link #contentType()} is not defined, this method returns false.
     *
     * @param mediaTypes media types that we check against
     * @return {@code true} if any of the media types provided matches the {@link #contentType()}
     */
    default boolean testContentType(MediaType... mediaTypes) {
        for (MediaType mediaType : mediaTypes) {
            if (mediaType.isWildcardType() && mediaType.isWildcardSubtype()) {
                return true;
            }
        }
        Optional<HttpMediaType> httpMediaType = contentType();
        if (httpMediaType.isEmpty()) {
            return false;
        }
        HttpMediaType contentType = httpMediaType.get();
        for (MediaType mediaType : mediaTypes) {
            if (contentType.test(mediaType)) {
                return true;
            }
        }
        return false;
    }
}
