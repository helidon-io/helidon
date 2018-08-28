/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Extends {@link Parameters} interface by adding HTTP response headers oriented constants and convenient methods.
 * Use constants located in {@link Http.Header} as standard header names.
 *
 * <h3>Lifecycle</h3>
 * Headers can be muted until {@link #send() send} to the client. It is also possible to register a '{@link #beforeSend(Consumer)
 * before send}' function which can made 'last minute mutation'.
 * <p>
 * Headers are send together with HTTP status code also automatically just before first chunk of response data is send.
 * See {@link ServerResponse} for detail.
 *
 * @see Http.Header
 * @see ServerResponse
 */
public interface ResponseHeaders extends Headers {

    /**
     * Gets immutable list of supported patch document formats (header {@value Http.Header#ACCEPT_PATCH}).
     * <p>
     * Method returns a copy of actual values.
     *
     * @return A list of supported media types for the patch.
     */
    List<MediaType> acceptPatches();

    /**
     * Adds one or more acceptedTypes path document formats (header {@value Http.Header#ACCEPT_PATCH}).
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
     * Optionally gets the value of {@value Http.Header#CONTENT_LENGTH} header.
     *
     * @return Length of the body in octets.
     */
    OptionalLong contentLength();

    /**
     * Sets the value of {@value Http.Header#CONTENT_LENGTH} header.
     *
     * @param contentLength Length of the body in octets.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void contentLength(long contentLength) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value Http.Header#EXPIRES} header.
     * <p>
     * Gives the date/time after which the response is considered stale.
     *
     * @return Expires header value.
     */
    Optional<ZonedDateTime> expires();

    /**
     * Sets the value of {@value Http.Header#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void expires(ZonedDateTime dateTime) throws AlreadyCompletedException;

    /**
     * Sets the value of {@value Http.Header#EXPIRES} header.
     * <p>
     * The date/time after which the response is considered stale.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void expires(Instant dateTime) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object.
     *
     * @return Expires header value.
     */
    Optional<ZonedDateTime> lastModified();

    /**
     * Sets the value of {@value Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object.
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void lastModified(ZonedDateTime dateTime) throws AlreadyCompletedException;

    /**
     * Sets the value of {@value Http.Header#LAST_MODIFIED} header.
     * <p>
     * The last modified date for the requested object
     *
     * @param dateTime Expires date/time.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void lastModified(Instant dateTime) throws AlreadyCompletedException;

    /**
     * Optionally gets the value of {@value Http.Header#LOCATION} header.
     * <p>
     * Used in redirection, or when a new resource has been created.
     *
     * @return Location header value.
     */
    Optional<URI> location();

    /**
     * Sets the value of {@value Http.Header#LOCATION} header.
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
     * @param name a name of the cookie.
     * @param value a value of the cookie.
     * @throws NullPointerException if {@code name} parameter is {@code null}.
     * @throws AlreadyCompletedException if headers were completed (sent to the client).
     */
    void addCookie(String name, String value) throws AlreadyCompletedException, NullPointerException;

    /**
     * Adds {@code Set-Cookie} header based on <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> with {@code Max-Age}
     * parameter.
     *
     * @param name a name of the cookie.
     * @param value a value of the cookie.
     * @param maxAge a {@code Max-Age} cookie parameter.
     * @throws NullPointerException if {@code name} parameter is {@code null}.
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
     */
    CompletionStage<ResponseHeaders> whenSend();

    /**
     * Send headers and status code to the client. This instance become immutable after that
     * (all muting methods throws {@link IllegalStateException}).
     * <p>
     * It is non-blocking method returning a {@link CompletionStage}.
     *
     * @return a completion stage of sending tryProcess.
     */
    CompletionStage<ResponseHeaders> send();

    /**
     * Represents {@code 'Set-Cookie'} header value specified by <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a> and
     * use in method {@link #addCookie(SetCookie)}.
     *
     * <p>It is mutable and fluent builder.
     */
    class SetCookie {

        private static final String PARAM_SEPARATOR = "; ";

        private final String name;
        private final String value;
        private ZonedDateTime expires;
        private Duration maxAge;
        private String domain;
        private String path;
        private boolean secure;
        private boolean httpOnly;

        /**
         * Creates new instance.
         *
         * @param name a cookie name.
         * @param value a cookie value.
         */
        public SetCookie(String name, String value) {
            Objects.requireNonNull(name, "Parameter 'name' is null!");
            //todo validate accepted characters
            this.name = name;
            this.value = value;
        }

        /**
         * Sets {@code Expires} parameter.
         *
         * @param expires an {@code Expires} parameter.
         * @return Updated instance.
         */
        SetCookie expires(ZonedDateTime expires) {
            this.expires = expires;
            return this;
        }

        /**
         * Sets {@code Expires} parameter.
         *
         * @param expires an {@code Expires} parameter.
         * @return Updated instance.
         */
        SetCookie expires(Instant expires) {
            if (expires == null) {
                this.expires = null;
            } else {
                this.expires = ZonedDateTime.ofInstant(expires, ZoneId.systemDefault());
            }
            return this;
        }

        /**
         * Sets {@code Max-Age} parameter.
         *
         * @param maxAge an {@code Max-Age} parameter.
         * @return Updated instance.
         */
        SetCookie maxAge(Duration maxAge) {
            this.maxAge = maxAge;
            return this;
        }

        /**
         * Sets {@code Domain} parameter.
         *
         * @param domain an {@code Domain} parameter.
         * @return Updated instance.
         */
        SetCookie domain(String domain) {
            this.domain = domain;
            return this;
        }

        /**
         * Sets {@code Path} parameter.
         *
         * @param path an {@code Path} parameter.
         * @return Updated instance.
         */
        SetCookie path(String path) {
            this.path = path;
            return this;
        }

        /**
         * Sets {@code Domain} and {@code Path} parameters.
         *
         * @param domainAndPath an URI to specify {@code Domain} and {@code Path} parameters.
         * @return Updated instance.
         */
        SetCookie domainAndPath(URI domainAndPath) {
            if (domainAndPath == null) {
                this.domain = null;
                this.path = null;
            } else {
                this.domain = domainAndPath.getHost();
                this.path = domainAndPath.getPath();
            }
            return this;
        }

        /**
         * Sets {@code Secure} parameter.
         *
         * @param secure an {@code Secure} parameter.
         * @return Updated instance.
         */
        SetCookie secure(boolean secure) {
            this.secure = secure;
            return this;
        }

        /**
         * Sets {@code HttpOnly} parameter.
         *
         * @param httpOnly an {@code HttpOnly} parameter.
         * @return Updated instance.
         */
        SetCookie httpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
            return this;
        }

        /**
         * Returns content of this instance as a 'Set-Cookie:' header value specified
         * by <a href="https://tools.ietf.org/html/rfc6265">RFC6265</a>.
         *
         * @return a 'Set-Cookie:' header value.
         */
        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append(name).append('=').append(value);
            if (expires != null) {
                result.append(PARAM_SEPARATOR);
                result.append("Expires=");
                result.append(expires.format(Http.DateTime.RFC_1123_DATE_TIME));
            }
            if (maxAge != null && !maxAge.isNegative() && !maxAge.isZero()) {
                result.append(PARAM_SEPARATOR);
                result.append("Max-Age=");
                result.append(maxAge.getSeconds());
            }
            if (domain != null) {
                result.append(PARAM_SEPARATOR);
                result.append("Domain=");
                result.append(domain);
            }
            if (path != null) {
                result.append(PARAM_SEPARATOR);
                result.append("Path=");
                result.append(path);
            }
            if (secure) {
                result.append(PARAM_SEPARATOR);
                result.append("Secure");
            }
            if (httpOnly) {
                result.append(PARAM_SEPARATOR);
                result.append("HttpOnly");
            }
            return result.toString();
        }
    }
}
