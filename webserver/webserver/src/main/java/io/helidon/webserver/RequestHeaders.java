/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.webserver;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.http.Headers;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;

/**
 * Extends {@link Parameters} interface by adding HTTP request headers oriented convenient methods.
 * Use constants located in {@link io.helidon.common.http.Http.Header} as standard header names.
 *
 * @see io.helidon.common.http.Http.Header
 */
public interface RequestHeaders extends Headers {

    /**
     * Optionally returns the MIME type of the body of the request.
     *
     * @return Media type of the content.
     */
    Optional<MediaType> contentType();

    /**
     * Optionally returns the length of the request body in octets (8-bit bytes).
     *
     * @return Length of the body in octets.
     */
    OptionalLong contentLength();

    /**
     * Returns cookies (parsed from '{@code Cookie:}' header) based on <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
     * It parse also older formats including RFC2965 but skips parameters. Only cookie {@code name} and {@code value} is returned.
     *
     * <p>Multiple cookies can be returned in a single headers and a single cookie-name can have multiple values.
     * Note that base on RFC6265 an order of cookie values has no semantics.
     *
     * @return An unmodifiable cookies represented by {@link Parameters} interface where key is a name of the cookie and
     * values are cookie values.
     */
    Parameters cookies();

    /**
     * Returns a list of acceptedTypes ({@value io.helidon.common.http.Http.Header#ACCEPT} header) content types in quality
     * factor order.
     * Never {@code null}.
     *
     * @return A list of acceptedTypes media types.
     */
    List<MediaType> acceptedTypes();

    /**
     * Test if provided type is acceptable as a response for this request.
     *
     * @param mediaType a media type to test if it is acceptable.
     * @return {@code true} if provided type is acceptable.
     * @throws NullPointerException if parameter {@code mediaType} is {@code null}.
     */
    boolean isAccepted(MediaType mediaType);

    /**
     * Optionally returns single media type from provided parameters which is best accepted by the client.
     * Method uses content negotiation {@value io.helidon.common.http.Http.Header#ACCEPT} header parameter and returns an empty
     * value in case
     * that nothing match.
     *
     * @param mediaTypes Supported media type candidates.
     * @return an accepted media type.
     */
    Optional<MediaType> bestAccepted(MediaType... mediaTypes);

    // TODO Add support for other accept headers.

    /**
     * Optionally returns acceptedTypes version in time ({@value io.helidon.common.http.Http.Header#ACCEPT_DATETIME} header).
     *
     * @return Acceptable version in time.
     */
    Optional<ZonedDateTime> acceptDatetime();

    /**
     * Optionally returns request date ({@value io.helidon.common.http.Http.Header#ACCEPT_DATETIME} header).
     *
     * @return Request date.
     */
    Optional<ZonedDateTime> date();

    /**
     * Optionally returns a value of {@value io.helidon.common.http.Http.Header#IF_MODIFIED_SINCE} header.
     * <p>
     * Allows a 304 Not Modified to be returned if content is unchanged.
     *
     * @return Content of {@value io.helidon.common.http.Http.Header#IF_MODIFIED_SINCE} header.
     */
    Optional<ZonedDateTime> ifModifiedSince();

    /**
     * Optionally returns a value of {@value io.helidon.common.http.Http.Header#IF_UNMODIFIED_SINCE} header.
     * <p>
     * <i>Only send the response if the entity has not been modified since a specific time.</i>
     *
     * @return Content of {@value io.helidon.common.http.Http.Header#IF_MODIFIED_SINCE} header.
     */
    Optional<ZonedDateTime> ifUnmodifiedSince();

    /**
     * Optionally returns the address of the previous web page (header {@value io.helidon.common.http.Http.Header#REFERER}) from which a link
     * to the currently requested page was followed.
     * <p>
     * <i>The word {@code referrer} has been misspelled in the RFC as well as in most implementations to the point that it
     * has become standard usage and is considered correct terminology</i>
     *
     * @return Referrers URI
     */
    Optional<URI> referer();

}
