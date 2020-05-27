/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;

import io.helidon.common.http.AlreadyCompletedException;
import io.helidon.common.http.Headers;
import io.helidon.common.http.MediaType;
import io.helidon.common.http.Parameters;
import io.helidon.common.http.SetCookie;
import io.helidon.common.reactive.Single;

/**
 * Extends {@link Parameters} interface by adding HTTP response headers oriented constants and convenient methods.
 * Use constants located in {@link io.helidon.common.http.Http.Header} as standard header names.
 *
 * <h3>Lifecycle</h3>
 * Headers can be muted until {@link #send() send} to the client. It is also possible to register a '{@link #beforeSend(Consumer)
 * before send}' function which can made 'last minute mutation'.
 * <p>
 * Headers are send together with HTTP status code also automatically just before first chunk of response data is send.
 *
 * @see io.helidon.common.http.Http.Header
 */
public interface ResponseHeaders extends Headers {

    /**
     * Gets immutable list of supported patch document formats (header {@value io.helidon.common.http.Http.Header#ACCEPT_PATCH}).
     * <p>
     * Method returns a copy of actual values.
     *
     * @return A list of supported media types for the patch.
     */
    List<MediaType> acceptPatches();

    /**
     * Adds one or more acceptedTypes path document formats (header {@value io.helidon.common.http.Http.Header#ACCEPT_PATCH}).
     *
     * @param acceptableMediaTypes media types to add.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void addAcceptPatches(MediaType... acceptableMediaTypes) throws AlreadyCompletedException;

    /**
     * Optionally gets the MIME type of the response body.
     *
     * @return Media type of the content.
     */
    Optional<MediaType> contentType();

    /**
     * Sets the MIME type of the response body.
     *
     * @param contentType Media type of the content.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void contentType(MediaType contentType) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value io.helidon.common.http.Http.Header#CONTENT_LENGTH} header.
     *
     * @return Length of the body in octets.
     */
    OptionalLong contentLength();

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#CONTENT_LENGTH} header.
     *
     * @param contentLength Length of the body in octets.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void contentLength(long contentLength) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value io.helidon.common.http.Http.Header#EXPIRES} header.
     * <p>
     * Gives the date/time after which the response is considered stale.
     *
     * @return Expires header value.
     */
    Optional<ZonedDateTime> expires();

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void expires(ZonedDateTime dateTime) throws AlreadyCompletedException;

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void expires(Instant dateTime) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value io.helidon.common.http.Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object.
     *
     * @return Expires header value.
     */
    Optional<ZonedDateTime> lastModified();

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void lastModified(ZonedDateTime dateTime) throws AlreadyCompletedException;

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void lastModified(Instant dateTime) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value io.helidon.common.http.Http.Header#LOCATION} header.
     * <p>
     * Used in redirection, or when a new resource has been created.
     *
     * @return Location header value.
     */
    Optional<URI> location();

    /**
     * Sets the value of {@value io.helidon.common.http.Http.Header#LOCATION} header.
     * <p>
     * Used in redirection, or when a new resource has been created.
     *
     * @param location Location header value.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void location(URI location) throws AlreadyCompletedException;

    /**
     * Adds {@code Set-Cookie} header based on <a href="https://tools.ietf.org/html/rfc2616">RFC2616</a>.
     *
     * @param name  a name of the cookie.
     * @param value a value of the cookie.
     * @throws NullPointerException      if {@code name} parameter is {@code null}.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void addCookie(String name, String value) throws AlreadyCompletedException, NullPointerException;

    /**
     * Adds {@code Set-Cookie} header based on <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> with {@code Max-Age}
     * parameter.
     *
     * @param name   a name of the cookie.
     * @param value  a value of the cookie.
     * @param maxAge a {@code Max-Age} cookie parameter.
     * @throws NullPointerException      if {@code name} parameter is {@code null}.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void addCookie(String name, String value, Duration maxAge) throws AlreadyCompletedException, NullPointerException;

    /**
     * Adds {@code Set-Cookie} header specified in <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
     *
     * @param cookie a cookie definition
     * @throws NullPointerException if {@code cookie} parameter is {@code null}
     */
    void addCookie(SetCookie cookie) throws NullPointerException;

    /**
     * Register a {@link Consumer} which is executed just before headers are send. {@code Consumer} can made 'last minute
     * changes' in headers.
     * <p>
     * Sending of headers to the client is postponed after all registered consumers are finished.
     * <p>
     * There is no guarantied execution order.
     *
     * @param headersConsumer a consumer which will be executed just before headers are send.
     */
    void beforeSend(Consumer<ResponseHeaders> headersConsumer);

    /**
     * Returns a completion stage which is completed when all headers are send to the client.
     *
     * @return a completion stage of the headers.
     * @deprecated since 2.0.0, please use {@link #whenSent()}
     */
    @Deprecated
    default Single<ResponseHeaders> whenSend() {
        return whenSent();
    }

    /**
     * Returns a {@link io.helidon.common.reactive.Single} which is completed when all headers are sent to the client.
     *
     * @return a single of the headers
     */
    Single<ResponseHeaders> whenSent();

    /**
     * Send headers and status code to the client. This instance become immutable after that
     * (all muting methods throws {@link IllegalStateException}).
     * <p>
     * It is non-blocking method returning a {@link io.helidon.common.reactive.Single}.
     *
     * @return a completion stage of sending process.
     */
    Single<ResponseHeaders> send();

}
