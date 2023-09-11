/*
 * Copyright (c) 2018, 2023 Oracle and/or its affiliates.
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

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import io.helidon.common.buffers.Ascii;
import io.helidon.common.buffers.BufferData;
import io.helidon.common.buffers.LazyString;
import io.helidon.common.mapper.Value;

/**
 * HTTP protocol related constants and utilities.
 * <p>
 * <b>Utility class</b>
 */
public final class Http {

    private Http() {
    }

    /**
     * Commonly used status codes defined by HTTP, see
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10">HTTP/1.1 documentation</a>.
     * Additional status codes can be added by applications
     * by call {@link #create(int)} or {@link #create(int, String)} with unkown status code, or with text
     * that differs from the predefined status codes.
     * <p>
     * Although the constants are instances of this class, they can be compared using instance equality, as the only
     * way to obtain an instance is through methods {@link #create(int)} {@link #create(int, String)}, which ensures
     * the same instance is returned for known status codes and reason phrases.
     * <p>
     * A good reference is the IANA list of HTTP Status Codes (we may not cover all of them in this type):
     * <a href="https://www.iana.org/assignments/http-status-codes/http-status-codes.xhtml">IANA HTTP Status Codes</a>
     */
    public static class Status {
        /**
         * 100 Continue,
         * see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.1.1">HTTP/1.1 documentations</a>.
         */
        public static final Status CONTINUE_100 = new Status(100, "Continue", true);
        /**
         * 101 Switching Protocols,
         * see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.1.2">HTTP/1.1 documentations</a>.
         */
        public static final Status SWITCHING_PROTOCOLS_101 = new Status(101, "Switching Protocols", true);
        /**
         * 200 OK, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.1">HTTP/1.1 documentation</a>.
         */
        public static final Status OK_200 = new Status(200, "OK", true);
        /**
         * 201 Created, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.2">HTTP/1.1 documentation</a>.
         */
        public static final Status CREATED_201 = new Status(201, "Created", true);
        /**
         * 202 Accepted, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.3">HTTP/1.1 documentation</a>
         * .
         */
        public static final Status ACCEPTED_202 = new Status(202, "Accepted", true);
        /**
         * 204 No Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.5">HTTP/1.1 documentation</a>.
         */
        public static final Status NO_CONTENT_204 = new Status(204, "No Content", true);
        /**
         * 205 Reset Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status RESET_CONTENT_205 = new Status(205, "Reset Content", true);
        /**
         * 206 Reset Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.7">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status PARTIAL_CONTENT_206 = new Status(206, "Partial Content", true);
        /**
         * 301 Moved Permanently, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.2">HTTP/1.1 documentation</a>.
         */
        public static final Status MOVED_PERMANENTLY_301 = new Status(301, "Moved Permanently", true);
        /**
         * 302 Found, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status FOUND_302 = new Status(302, "Found", true);
        /**
         * 303 See Other, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.4">HTTP/1.1 documentation</a>.
         */
        public static final Status SEE_OTHER_303 = new Status(303, "See Other", true);
        /**
         * 304 Not Modified, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5">HTTP/1.1 documentation</a>.
         */
        public static final Status NOT_MODIFIED_304 = new Status(304, "Not Modified", true);
        /**
         * 305 Use Proxy, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status USE_PROXY_305 = new Status(305, "Use Proxy", true);
        /**
         * 307 Temporary Redirect, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.8">HTTP/1.1 documentation</a>.
         */
        public static final Status TEMPORARY_REDIRECT_307 = new Status(307, "Temporary Redirect", true);
        /**
         * 308 Permanent Redirect, see
         * <a href="https://www.rfc-editor.org/rfc/rfc7538">HTTP Status Code 308 documentation</a>.
         */
        public static final Status PERMANENT_REDIRECT_308 = new Status(308, "Permanent Redirect", true);
        /**
         * 400 Bad Request, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.1">HTTP/1.1 documentation</a>.
         */
        public static final Status BAD_REQUEST_400 = new Status(400, "Bad Request", true);
        /**
         * 401 Unauthorized, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.2">HTTP/1.1 documentation</a>.
         */
        public static final Status UNAUTHORIZED_401 = new Status(401, "Unauthorized", true);
        /**
         * 402 Payment Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status PAYMENT_REQUIRED_402 = new Status(402, "Payment Required", true);
        /**
         * 403 Forbidden, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.4">HTTP/1.1 documentation</a>.
         */
        public static final Status FORBIDDEN_403 = new Status(403, "Forbidden", true);
        /**
         * 404 Not Found, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.5">HTTP/1.1 documentation</a>.
         */
        public static final Status NOT_FOUND_404 = new Status(404, "Not Found", true);
        /**
         * 405 Method Not Allowed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status METHOD_NOT_ALLOWED_405 = new Status(405, "Method Not Allowed", true);
        /**
         * 406 Not Acceptable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.7">HTTP/1.1 documentation</a>.
         */
        public static final Status NOT_ACCEPTABLE_406 = new Status(406, "Not Acceptable", true);
        /**
         * 407 Proxy Authentication Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.8">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status PROXY_AUTHENTICATION_REQUIRED_407 = new Status(407, "Proxy Authentication Required", true);
        /**
         * 408 Request Timeout, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.9">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status REQUEST_TIMEOUT_408 = new Status(408, "Request Timeout", true);
        /**
         * 409 Conflict, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.10">HTTP/1.1 documentation</a>.
         */
        public static final Status CONFLICT_409 = new Status(409, "Conflict", true);
        /**
         * 410 Gone, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.11">HTTP/1.1 documentation</a>.
         */
        public static final Status GONE_410 = new Status(410, "Gone", true);
        /**
         * 411 Length Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.12">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status LENGTH_REQUIRED_411 = new Status(411, "Length Required", true);
        /**
         * 412 Precondition Failed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.13">HTTP/1.1 documentation</a>.
         */
        public static final Status PRECONDITION_FAILED_412 = new Status(412, "Precondition Failed", true);
        /**
         * 413 Request Entity Too Large, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.14">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status REQUEST_ENTITY_TOO_LARGE_413 = new Status(413, "Request Entity Too Large", true);
        /**
         * 414 Request-URI Too Long, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.15">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status REQUEST_URI_TOO_LONG_414 = new Status(414, "Request-URI Too Long", true);
        /**
         * 415 Unsupported Media Type, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.16">HTTP/1.1 documentation</a>.
         */
        public static final Status UNSUPPORTED_MEDIA_TYPE_415 = new Status(415, "Unsupported Media Type", true);
        /**
         * 416 Requested Range Not Satisfiable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.17">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status REQUESTED_RANGE_NOT_SATISFIABLE_416 = new Status(416, "Requested Range Not Satisfiable", true);
        /**
         * 417 Expectation Failed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.18">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status EXPECTATION_FAILED_417 = new Status(417, "Expectation Failed", true);
        /**
         * 418 I'm a teapot, see
         * <a href="https://tools.ietf.org/html/rfc2324#section-2.3.2">Hyper Text Coffee Pot Control Protocol (HTCPCP/1.0)</a>.
         */
        public static final Status I_AM_A_TEAPOT_418 = new Status(418, "I'm a teapot", true);
        /**
         * Misdirected request, see
         * <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-421-misdirected-request">RFC 9110 - Http Semantics</a>.
         */
        public static final Status MISDIRECTED_REQUEST_421 = new Status(421, "Misdirected Request", true);
        /**
         * Unprocessable content, see
         * <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-422-unprocessable-content">RFC 9110 - Http Semantics</a>.
         */
        public static final Status UNPROCESSABLE_CONTENT_422 = new Status(422, "Unprocessable Content", true);
        /**
         * Locked, see
         * <a href="https://www.rfc-editor.org/rfc/rfc4918.html#page-78">RFC 4918 - HTTP Extensions for WebDAV</a>.
         */
        public static final Status LOCKED_423 = new Status(423, "Locked", true);
        /**
         * Failed dependency, see
         * <a href="https://www.rfc-editor.org/rfc/rfc4918.html#section-11.4">RFC 4918 - HTTP Extensions for WebDAV</a>.
         */
        public static final Status FAILED_DEPENDENCY_424 = new Status(424, "Failed Dependency", true);
        /**
         * Upgrade required, see
         * <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-426-upgrade-required">RFC 9110 - Http Semantics</a>.
         */
        public static final Status UPGRADE_REQUIRED_426 = new Status(426, "Upgrade Required", true);
        /**
         * Precondition required, see
         * <a href="https://www.rfc-editor.org/rfc/rfc6585.html#page-2">RFC 6585 - Additional HTTP Status Codes</a>.
         */
        public static final Status PRECONDITION_REQUIRED_428 = new Status(428, "Precondition Required", true);
        /**
         * Too many requests, see
         * <a href="https://www.rfc-editor.org/rfc/rfc6585.html#page-3">RFC 6585 - Additional HTTP Status Codes</a>.
         */
        public static final Status TOO_MANY_REQUESTS_429 = new Status(429, "Too Many Requests", true);
        /**
         * 500 Internal Server Error, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.1">HTTP/1.1 documentation</a>.
         */
        public static final Status INTERNAL_SERVER_ERROR_500 = new Status(500, "Internal Server Error", true);
        /**
         * 501 Not Implemented, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.2">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status NOT_IMPLEMENTED_501 = new Status(501, "Not Implemented", true);
        /**
         * 502 Bad Gateway, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status BAD_GATEWAY_502 = new Status(502, "Bad Gateway", true);
        /**
         * 503 Service Unavailable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.4">HTTP/1.1 documentation</a>.
         */
        public static final Status SERVICE_UNAVAILABLE_503 = new Status(503, "Service Unavailable", true);
        /**
         * 504 Gateway Timeout, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.5">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        public static final Status GATEWAY_TIMEOUT_504 = new Status(504, "Gateway Timeout", true);
        /**
         * 505 HTTP Version Not Supported, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.6">HTTP/1.1 documentation</a>.
         *
         * @since 3.0.3
         */
        public static final Status HTTP_VERSION_NOT_SUPPORTED_505 = new Status(505, "HTTP Version Not Supported", true);

        static {
            // THIS MUST BE AFTER THE LAST CONSTANT
            StatusHelper.statusesDone();
        }

        private final int code;
        private final String reason;
        private final Family family;
        private final String codeText;
        private final String stringValue;

        private Status(int statusCode, String reasonPhrase, boolean instance) {
            this.code = statusCode;
            this.reason = reasonPhrase;
            this.family = Family.of(statusCode);
            this.codeText = String.valueOf(code);
            this.stringValue = code + " " + reason;

            if (instance) {
                StatusHelper.add(this);
            }
        }

        private Status(int statusCode, String reasonPhrase, Family family, String codeText) {
            // for custom codes
            this.code = statusCode;
            this.reason = reasonPhrase;
            this.family = family;
            this.codeText = codeText;
            this.stringValue = code + " " + reason;
        }

        /**
         * Convert a numerical status code into the corresponding Status.
         * <p>
         * For an unknown code, an ad-hoc {@link io.helidon.http.Http.Status} is created.
         *
         * @param statusCode the numerical status code
         * @return the matching Status; either a constant from this class, or an ad-hoc {@link io.helidon.http.Http.Status}
         */
        public static Status create(int statusCode) {
            Status found = StatusHelper.find(statusCode);

            if (found == null) {
                return createNew(Family.of(statusCode), statusCode, "", String.valueOf(statusCode));
            }
            return found;
        }

        /**
         * Convert a numerical status code into the corresponding Status.
         * <p>
         * It either returns an existing {@link io.helidon.http.Http.Status} constant if possible.
         * For an unknown code, or code/reason phrase combination it creates
         * an ad-hoc {@link io.helidon.http.Http.Status}.
         *
         * @param statusCode   the numerical status code
         * @param reasonPhrase the reason phrase; if {@code null} or a known reason phrase, an instance with the default
         *                     phrase is returned; otherwise, a new instance is returned
         * @return the matching Status
         */
        public static Status create(int statusCode, String reasonPhrase) {
            Status found = StatusHelper.find(statusCode);
            if (found == null) {
                return createNew(Family.of(statusCode), statusCode, reasonPhrase, String.valueOf(statusCode));
            }
            if (reasonPhrase == null) {
                return found;
            }
            if (found.reasonPhrase().equalsIgnoreCase(reasonPhrase)) {
                return found;
            }
            return createNew(found.family(), statusCode, reasonPhrase, found.codeText());
        }

        private static Status createNew(Family family, int statusCode, String reasonPhrase, String codeText) {
            return new Status(statusCode, reasonPhrase, family, codeText);
        }

        /**
         * Get the associated integer value representing the status code.
         *
         * @return the integer value representing the status code.
         */
        public int code() {
            return code;
        }

        /**
         * Get the class of status code.
         *
         * @return the class of status code.
         */
        public Family family() {
            return family;
        }

        /**
         * Get the reason phrase.
         *
         * @return the reason phrase.
         */
        public String reasonPhrase() {
            return reason;
        }

        /**
         * Text of the {@link #code()}.
         *
         * @return code string (number as a string)
         */
        public String codeText() {
            return codeText;
        }

        /**
         * Get the response status as string.
         *
         * @return the response status string in the form of a partial HTTP response status line,
         *         i.e. {@code "Status-Code SP Reason-Phrase"}.
         */
        public String toString() {
            return stringValue;
        }

        /**
         * Text of the status as used in HTTP/1, such as "200 OK".
         * @return text of this status
         */
        public String text() {
            return stringValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Status status = (Status) o;
            return code == status.code && reason.equals(status.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(code, reason);
        }

        /**
         * An enumeration representing the class of status code. Family is used
         * here since class is overloaded in Java.
         * <p>
         * Copied from JAX-RS.
         */
        public enum Family {

            /**
             * {@code 1xx} HTTP status codes.
             */
            INFORMATIONAL,
            /**
             * {@code 2xx} HTTP status codes.
             */
            SUCCESSFUL,
            /**
             * {@code 3xx} HTTP status codes.
             */
            REDIRECTION,
            /**
             * {@code 4xx} HTTP status codes.
             */
            CLIENT_ERROR,
            /**
             * {@code 5xx} HTTP status codes.
             */
            SERVER_ERROR,
            /**
             * Other, unrecognized HTTP status codes.
             */
            OTHER;

            /**
             * Get the family for the response status code.
             *
             * @param statusCode response status code to get the family for.
             * @return family of the response status code.
             */
            public static Family of(int statusCode) {
                return switch (statusCode / 100) {
                    case 1 -> Family.INFORMATIONAL;
                    case 2 -> Family.SUCCESSFUL;
                    case 3 -> Family.REDIRECTION;
                    case 4 -> Family.CLIENT_ERROR;
                    case 5 -> Family.SERVER_ERROR;
                    default -> Family.OTHER;
                };
            }
        }
    }

    /**
     * HTTP header name.
     */
    public sealed interface HeaderName permits HeaderNameImpl, HeaderNameEnum {
        /**
         * Lowercase value of this header, used by HTTP/2, may be used for lookup by HTTP/1.
         * There is no validation of this value, so if this contains an upper-case letter, behavior
         * is undefined.
         *
         * @return name of the header, lowercase
         */
        String lowerCase();

        /**
         * Header name as used in HTTP/1, or "human-readable" value of this header.
         *
         * @return name of the header, may use uppercase and/or lowercase
         */
        String defaultCase();

        /**
         * Index of this header (if one of the known indexed headers), or {@code -1} if this is a custom header name.
         *
         * @return index of this header
         */
        default int index() {
            return -1;
        }

        /**
         * Http2 defines pseudoheaders as headers starting with a {@code :} character. These are used instead
         * of the prologue line from HTTP/1 (to define path, authority etc.) and instead of status line in response.
         *
         * @return whether this header is a pseudo-header
         */
        default boolean isPseudoHeader() {
            return lowerCase().charAt(0) == ':';
        }
    }

    /**
     * HTTP Header with {@link io.helidon.http.Http.HeaderName} and value.
     *
     * @see io.helidon.http.Http.Headers
     */
    public interface Header extends Value<String> {

        /**
         * Name of the header as configured by user
         * or as received on the wire.
         *
         * @return header name, always lower case for HTTP/2 headers
         */
        @Override
        String name();

        /**
         * Value of the header.
         *
         * @return header value
         * @deprecated use {@link #get()}
         */
        @Deprecated(forRemoval = true, since = "4.0.0")
        default String value() {
            return get();
        }

        /**
         * Header name for the header.
         *
         * @return header name
         */
        HeaderName headerName();

        /**
         * All values concatenated using a comma.
         *
         * @return all values joined by a comma
         */
        default String values() {
            return String.join(",", allValues());
        }

        /**
         * All values of this header.
         *
         * @return all configured values
         */
        List<String> allValues();

        /**
         * All values of this header. If this header is defined as a single header with comma separated values,
         * set {@code split} to true.
         *
         * @param split whether to split single value by comma, does nothing if the value is already a list.
         * @return list of values
         */
        default List<String> allValues(boolean split) {
            if (split) {
                List<String> values = allValues();
                if (values.size() == 1) {
                    String value = values.get(0);
                    if (value.contains(", ")) {
                        return List.of(value.split(", "));
                    } else {
                        return List.of(value);
                    }
                }
                return values;
            } else {
                return allValues();
            }
        }

        /**
         * Number of values this header has.
         *
         * @return number of values (minimal number is 1)
         */
        int valueCount();

        /**
         * Sensitive headers should not be logged, or indexed (HTTP/2).
         *
         * @return whether this header is sensitive
         */
        boolean sensitive();

        /**
         * Changing headers should not be cached, and their value should not be indexed (HTTP/2).
         *
         * @return whether this header's value is changing often
         */
        boolean changing();

        /**
         * Cached bytes of a single valued header's value.
         *
         * @return value bytes
         */
        default byte[] valueBytes() {
            return get().getBytes(StandardCharsets.US_ASCII);
        }

        /**
         * Write the current header as an HTTP header to the provided buffer.
         *
         * @param buffer buffer to write to (should be growing)
         */
        default void writeHttp1Header(BufferData buffer) {
            byte[] nameBytes = name().getBytes(StandardCharsets.US_ASCII);
            if (valueCount() == 1) {
                writeHeader(buffer, nameBytes, valueBytes());
            } else {
                for (String value : allValues()) {
                    writeHeader(buffer, nameBytes, value.getBytes(StandardCharsets.US_ASCII));
                }
            }
        }

        /**
         * Check validity of header name and values.
         *
         * @throws IllegalArgumentException in case the HeaderValue is not valid
         */
        default void validate() throws IllegalArgumentException {
            String name = name();
            // validate that header name only contains valid characters
            HttpToken.validate(name);
            // Validate header value
            validateValue(name, values());
        }


        // validate header value based on https://www.rfc-editor.org/rfc/rfc7230#section-3.2 and throws IllegalArgumentException
        // if invalid.
        private static void validateValue(String name, String value) throws IllegalArgumentException {
            char[] vChars = value.toCharArray();
            int vLength = vChars.length;
            for (int i = 0; i < vLength; i++) {
                char vChar = vChars[i];
                if (i == 0) {
                    if (vChar < '!' || vChar == '\u007f') {
                        throw new IllegalArgumentException("First character of the header value is invalid"
                                                                   + " for header '" + name + "'");
                    }
                } else {
                    if (vChar < ' ' && vChar != '\t' || vChar == '\u007f') {
                        throw new IllegalArgumentException("Character at position " + (i + 1) + " of the header value is invalid"
                                                                   + " for header '" + name + "'");
                    }
                }
            }
        }

        private void writeHeader(BufferData buffer, byte[] nameBytes, byte[] valueBytes) {
            // header name
            buffer.write(nameBytes);
            // ": "
            buffer.write(':');
            buffer.write(' ');
            // header value
            buffer.write(valueBytes);
            // \r\n
            buffer.write('\r');
            buffer.write('\n');
        }
    }

    /**
     * Mutable header value.
     */
    public interface HeaderValueWriteable extends Header {
        /**
         * Create a new mutable header from an existing header.
         *
         * @param header header to copy
         * @return a new mutable header
         */
        static HeaderValueWriteable create(Header header) {
            return new HeaderValueCopy(header);
        }

        /**
         * Add a value to this header.
         *
         * @param value value to add
         * @return this instance
         */
        HeaderValueWriteable addValue(String value);
    }

    /**
     * Utility class with a list of names of standard HTTP headers and related tooling methods.
     */
    public static final class HeaderNames {
        /**
         * The {@code Accept} header name.
         * Content-Types that are acceptedTypes for the response.
         */
        public static final HeaderName ACCEPT = HeaderNameEnum.ACCEPT;
        /**
         * The {@code Accept-Charset} header name.
         * Character sets that are acceptedTypes.
         */
        public static final HeaderName ACCEPT_CHARSET = HeaderNameEnum.ACCEPT_CHARSET;
        /**
         * The {@code Accept-Encoding} header name.
         * List of acceptedTypes encodings.
         */
        public static final HeaderName ACCEPT_ENCODING = HeaderNameEnum.ACCEPT_ENCODING;
        /**
         * The {@code Accept-Language} header name.
         * List of acceptedTypes human languages for response.
         */
        public static final HeaderName ACCEPT_LANGUAGE = HeaderNameEnum.ACCEPT_LANGUAGE;
        /**
         * The {@code Accept-Datetime} header name.
         * Acceptable version in time.
         */
        public static final HeaderName ACCEPT_DATETIME = HeaderNameEnum.ACCEPT_DATETIME;
        /**
         * The {@code Access-Control-Allow-Credentials} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_ALLOW_CREDENTIALS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_CREDENTIALS;
        /**
         * The {@code Access-Control-Allow-Headers} header name.
         * CORS configuration
         */
        public static final HeaderName ACCESS_CONTROL_ALLOW_HEADERS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_HEADERS;
        /**
         * The {@code Access-Control-Allow-Methods} header name.
         * CORS configuration
         */
        public static final HeaderName ACCESS_CONTROL_ALLOW_METHODS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_METHODS;
        /**
         * The {@code Access-Control-Allow-Origin} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_ALLOW_ORIGIN = HeaderNameEnum.ACCESS_CONTROL_ALLOW_ORIGIN;
        /**
         * The {@code Access-Control-Expose-Headers} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_EXPOSE_HEADERS = HeaderNameEnum.ACCESS_CONTROL_EXPOSE_HEADERS;
        /**
         * The {@code Access-Control-Max-Age} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_MAX_AGE = HeaderNameEnum.ACCESS_CONTROL_MAX_AGE;
        /**
         * The {@code Access-Control-Request-Headers} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_REQUEST_HEADERS = HeaderNameEnum.ACCESS_CONTROL_REQUEST_HEADERS;
        /**
         * The {@code Access-Control-Request-Method} header name.
         * CORS configuration.
         */
        public static final HeaderName ACCESS_CONTROL_REQUEST_METHOD = HeaderNameEnum.ACCESS_CONTROL_REQUEST_METHOD;
        /**
         * The {@code Authorization} header name.
         * Authentication credentials for HTTP authentication.
         */
        public static final HeaderName AUTHORIZATION = HeaderNameEnum.AUTHORIZATION;
        /**
         * The {@code Cookie} header name.
         * An HTTP cookie previously sent by the server with {@code Set-Cookie}.
         */
        public static final HeaderName COOKIE = HeaderNameEnum.COOKIE;
        /**
         * The {@code Expect} header name.
         * Indicates that particular server behaviors are required by the client.
         */
        public static final HeaderName EXPECT = HeaderNameEnum.EXPECT;
        /**
         * The {@code Forwarded} header name.
         * Disclose original information of a client connecting to a web server through an HTTP proxy.
         */
        public static final HeaderName FORWARDED = HeaderNameEnum.FORWARDED;
        /**
         * The {@code From} header name.
         * The email address of the user making the request.
         */
        public static final HeaderName FROM = HeaderNameEnum.FROM;
        /**
         * The {@code Host} header name.
         * The domain name of the server (for virtual hosting), and the TCP port number on which the server is listening.
         * The port number may be omitted if the port is the standard port for the service requested.
         */
        public static final HeaderName HOST = HeaderNameEnum.HOST;
        /**
         * The {@value} header.
         *
         * @see #HOST
         */
        public static final String HOST_STRING = "Host";
        /**
         * The {@code If-Match} header name.
         * Only perform the action if the client supplied entity matches the same entity on the server. This is mainly
         * for methods like PUT to only update a resource if it has not been modified since the user last updated it.
         */
        public static final HeaderName IF_MATCH = HeaderNameEnum.IF_MATCH;
        /**
         * The {@code If-Modified-Since} header name.
         * Allows a 304 Not Modified to be returned if content is unchanged.
         */
        public static final HeaderName IF_MODIFIED_SINCE = HeaderNameEnum.IF_MODIFIED_SINCE;
        /**
         * The {@code If-None-Match} header name.
         * Allows a 304 Not Modified to be returned if content is unchanged, based on {@link #ETAG}.
         */
        public static final HeaderName IF_NONE_MATCH = HeaderNameEnum.IF_NONE_MATCH;
        /**
         * The {@code If-Range} header name.
         * If the entity is unchanged, send me the part(s) that I am missing; otherwise, send me the entire new entity.
         */
        public static final HeaderName IF_RANGE = HeaderNameEnum.IF_RANGE;
        /**
         * The {@code If-Unmodified-Since} header name.
         * Only send The {@code response if The Entity} has not been modified since a specific time.
         */
        public static final HeaderName IF_UNMODIFIED_SINCE = HeaderNameEnum.IF_UNMODIFIED_SINCE;
        /**
         * The {@code Max-Forwards} header name.
         * Limit the number of times the message can be forwarded through proxies or gateways.
         */
        public static final HeaderName MAX_FORWARDS = HeaderNameEnum.MAX_FORWARDS;
        /**
         * The {@code <code>{@value}</code>} header name.
         * Initiates a request for cross-origin resource sharing (asks server for an {@code 'Access-Control-Allow-Origin'}
         * response field).
         */
        public static final HeaderName ORIGIN = HeaderNameEnum.ORIGIN;
        /**
         * The {@code Proxy-Authenticate} header name.
         * Proxy authentication information.
         */
        public static final HeaderName PROXY_AUTHENTICATE = HeaderNameEnum.PROXY_AUTHENTICATE;
        /**
         * The {@code Proxy-Authorization} header name.
         * Proxy authorization information.
         */
        public static final HeaderName PROXY_AUTHORIZATION = HeaderNameEnum.PROXY_AUTHORIZATION;
        /**
         * The {@code Range} header name.
         * Request only part of an entity. Bytes are numbered from 0.
         */
        public static final HeaderName RANGE = HeaderNameEnum.RANGE;
        /**
         * The {@code <code>{@value}</code>} header name.
         * This is the address of the previous web page from which a link to the currently requested page was followed.
         * (The {@code word <i>referrer</i>} has been misspelled in The
         * {@code RFC as well as in most implementations to the point that it} has
         * become standard usage and is considered correct terminology.)
         */
        public static final HeaderName REFERER = HeaderNameEnum.REFERER;
        /**
         * The {@code <code>{@value}</code>} header name.
         */
        public static final HeaderName REFRESH = HeaderNameEnum.REFRESH;
        /**
         * The {@code <code>{@value}</code>} header name.
         * The {@code transfer encodings the user agent is willing to acceptedTypes: the same values as for The Response} header
         * field
         * {@code Transfer-Encoding} can be used, plus the <i>trailers</i> value (related to the <i>chunked</i> transfer method)
         * to notify the server it expects to receive additional fields in the trailer after the last, zero-sized, chunk.
         */
        public static final HeaderName TE = HeaderNameEnum.TE;
        /**
         * The {@code User-Agent} header name.
         * The user agent string of the user agent.
         */
        public static final HeaderName USER_AGENT = HeaderNameEnum.USER_AGENT;
        /**
         * The {@code Via} header name.
         * Informs the server of proxies through which the request was sent.
         */
        public static final HeaderName VIA = HeaderNameEnum.VIA;
        /**
         * The {@code Accept-Patch} header name.
         * Specifies which patch document formats this server supports.
         */
        public static final HeaderName ACCEPT_PATCH = HeaderNameEnum.ACCEPT_PATCH;
        /**
         * The {@code Accept-Ranges} header name.
         * What partial content range types this server supports via byte serving.
         */
        public static final HeaderName ACCEPT_RANGES = HeaderNameEnum.ACCEPT_RANGES;
        /**
         * The {@code Age} header name.
         * The {@code age The Object} has been in a proxy cache in seconds.
         */
        public static final HeaderName AGE = HeaderNameEnum.AGE;
        /**
         * The {@code Allow} header name.
         * Valid actions for a specified resource. To be used for a 405 Method not allowed.
         */
        public static final HeaderName ALLOW = HeaderNameEnum.ALLOW;
        /**
         * The {@code <code>{@value}</code>} header name.
         * A server uses <i>Alt-Svc</i> header (meaning Alternative Services) to indicate that its resources can also be
         * accessed at a different network location (host or port) or using a different protocol.
         */
        public static final HeaderName ALT_SVC = HeaderNameEnum.ALT_SVC;
        /**
         * The {@code Cache-Control} header name.
         * Tells all caching mechanisms from server to client whether they may cache this object. It is measured in seconds.
         */
        public static final HeaderName CACHE_CONTROL = HeaderNameEnum.CACHE_CONTROL;
        /**
         * The {@code Connection} header name.
         * Control options for The {@code current connection and list of} hop-by-hop response fields.
         */
        public static final HeaderName CONNECTION = HeaderNameEnum.CONNECTION;
        /**
         * The {@code <code>{@value}</code>} header name.
         * An opportunity to raise a <i>File Download</i> dialogue box for a known MIME type with binary format or suggest
         * a filename for dynamic content. Quotes are necessary with special characters.
         */
        public static final HeaderName CONTENT_DISPOSITION = HeaderNameEnum.CONTENT_DISPOSITION;
        /**
         * The {@code Content-Encoding} header name.
         * The type of encoding used on the data.
         */
        public static final HeaderName CONTENT_ENCODING = HeaderNameEnum.CONTENT_ENCODING;
        /**
         * The {@code Content-Language} header name.
         * The natural language or languages of the intended audience for the enclosed content.
         */
        public static final HeaderName CONTENT_LANGUAGE = HeaderNameEnum.CONTENT_LANGUAGE;
        /**
         * The {@code Content-Length} header name.
         * The length of the response body in octets.
         */
        public static final HeaderName CONTENT_LENGTH = HeaderNameEnum.CONTENT_LENGTH;
        /**
         * The {@code Content-Location} header name.
         * An alternate location for the returned data.
         */
        public static final HeaderName CONTENT_LOCATION = HeaderNameEnum.CONTENT_LOCATION;
        /**
         * The {@code Content-Range} header name.
         * Where in a full body message this partial message belongs.
         */
        public static final HeaderName CONTENT_RANGE = HeaderNameEnum.CONTENT_RANGE;
        /**
         * The {@code Content-Type} header name.
         * The MIME type of this content.
         */
        public static final HeaderName CONTENT_TYPE = HeaderNameEnum.CONTENT_TYPE;
        /**
         * The {@code Date} header name.
         * The date and time that the message was sent (in <i>HTTP-date</i> format as defined by RFC 7231).
         */
        public static final HeaderName DATE = HeaderNameEnum.DATE;
        /**
         * The {@code Etag} header name.
         * An identifier for a specific version of a resource, often a message digest.
         */
        public static final HeaderName ETAG = HeaderNameEnum.ETAG;
        /**
         * The {@code Expires} header name.
         * Gives the date/time after which the response is considered stale (in <i>HTTP-date</i> format as defined by RFC 7231)
         */
        public static final HeaderName EXPIRES = HeaderNameEnum.EXPIRES;
        /**
         * The {@code Last-Modified} header name.
         * The last modified date for the requested object (in <i>HTTP-date</i> format as defined by RFC 7231)
         */
        public static final HeaderName LAST_MODIFIED = HeaderNameEnum.LAST_MODIFIED;
        /**
         * The {@code Link} header name.
         * Used to express a typed relationship with another resource, where the relation type is defined by RFC 5988.
         */
        public static final HeaderName LINK = HeaderNameEnum.LINK;
        /**
         * The {@code Location} header name.
         * Used in redirection, or whenRequest a new resource has been created.
         */
        public static final HeaderName LOCATION = HeaderNameEnum.LOCATION;
        /**
         * The {@code Pragma} header name.
         * Implementation-specific fields that may have various effects anywhere along the request-response chain.
         */
        public static final HeaderName PRAGMA = HeaderNameEnum.PRAGMA;
        /**
         * The {@code Public-Key-Pins} header name.
         * HTTP Public Key Pinning, announces hash of website's authentic TLS certificate.
         */
        public static final HeaderName PUBLIC_KEY_PINS = HeaderNameEnum.PUBLIC_KEY_PINS;
        /**
         * The {@code <code>{@value}</code>} header name.
         * If an entity is temporarily unavailable, this instructs the client to try again later. Value could be a specified
         * period of time (in seconds) or an HTTP-date.
         */
        public static final HeaderName RETRY_AFTER = HeaderNameEnum.RETRY_AFTER;
        /**
         * The {@code Server} header name.
         * A name for the server.
         */
        public static final HeaderName SERVER = HeaderNameEnum.SERVER;
        /**
         * The {@code Set-Cookie} header name.
         * An HTTP cookie set directive.
         */
        public static final HeaderName SET_COOKIE = HeaderNameEnum.SET_COOKIE;
        /**
         * The {@code Set-Cookie2} header name.
         * An HTTP cookie set directive.
         */
        public static final HeaderName SET_COOKIE2 = HeaderNameEnum.SET_COOKIE2;
        /**
         * The {@code Strict-Transport-Security} header name.
         * A HSTS Policy informing The {@code HTTP client} how long to cache the HTTPS only policy and whether this applies to
         * subdomains.
         */
        public static final HeaderName STRICT_TRANSPORT_SECURITY = HeaderNameEnum.STRICT_TRANSPORT_SECURITY;
        /**
         * The {@code Trailer} header name.
         * The Trailer general field value indicates that the given set of} header fields is present in the trailer of
         * a message encoded with chunked transfer coding.
         */
        public static final HeaderName TRAILER = HeaderNameEnum.TRAILER;
        /**
         * The {@code Transfer-Encoding} header name.
         * The form of encoding used to safely transfer the entity to the user. Currently defined methods are:
         * {@code chunked, compress, deflate, gzip, identity}.
         */
        public static final HeaderName TRANSFER_ENCODING = HeaderNameEnum.TRANSFER_ENCODING;
        /**
         * The {@code Tsv} header name.
         * Tracking Status Value, value suggested to be sent in response to a DNT(do-not-track).
         */
        public static final HeaderName TSV = HeaderNameEnum.TSV;
        /**
         * The {@code Upgrade} header name.
         * Ask to upgrade to another protocol.
         */
        public static final HeaderName UPGRADE = HeaderNameEnum.UPGRADE;
        /**
         * The {@code Vary} header name.
         * Tells downstream proxies how to match future request headers to decide whether the cached response can be used rather
         * than requesting a fresh one from the origin server.
         */
        public static final HeaderName VARY = HeaderNameEnum.VARY;
        /**
         * The {@code Warning} header name.
         * A general warning about possible problems with the entity body.
         */
        public static final HeaderName WARNING = HeaderNameEnum.WARNING;
        /**
         * The {@code WWW-Authenticate} header name.
         * Indicates the authentication scheme that should be used to access the requested entity.
         */
        public static final HeaderName WWW_AUTHENTICATE = HeaderNameEnum.WWW_AUTHENTICATE;
        /**
         * The {@code X_HELIDON_CN} header name.
         * Corresponds to the certificate CN subject value when client authentication enabled.
         * This header will be removed if it is part of the request.
         */
        public static final HeaderName X_HELIDON_CN = HeaderNameEnum.X_HELIDON_CN;
        /**
         * The {@code X-Forwarded-For} header name.
         * Represents the originating client and intervening proxies when the request has passed through one or more proxies.
         */
        public static final HeaderName X_FORWARDED_FOR = HeaderNameEnum.X_FORWARDED_FOR;
        /**
         * The {@code X_FORWARDED_HOST} header name.
         * Represents the host specified by the originating client when the request has passed through one or more proxies.
         */
        public static final HeaderName X_FORWARDED_HOST = HeaderNameEnum.X_FORWARDED_HOST;

        /**
         * The {@code X_FORWARDED_PORT} header name.
         * Represents the port specified by the originating client when the request has passed through one or more proxies.
         */
        public static final HeaderName X_FORWARDED_PORT = HeaderNameEnum.X_FORWARDED_PORT;

        /**
         * The {@code X_FORWARDED_PREFIX} header name.
         * Represents the path prefix to be applied to relative paths resolved against this request when the request has passed
         * through one or more proxies.
         *
         */
        public static final HeaderName X_FORWARDED_PREFIX = HeaderNameEnum.X_FORWARDED_PREFIX;
        /**
         * The {@code X_FORWARDED_PROTO} header name.
         * Represents the protocol specified by the originating client when the request has passed through one or more proxies.
         */
        public static final HeaderName X_FORWARDED_PROTO = HeaderNameEnum.X_FORWARDED_PROTO;

        private HeaderNames() {
        }

        /**
         * Find or create a header name.
         * If a known indexed header exists for the name, the instance is returned.
         * Otherwise a new header name is created with the provided name.
         *
         * @param name default case to use for custom header names (header names not known by Helidon)
         * @return header name instance
         */
        public static HeaderName create(String name) {
            HeaderName headerName = HeaderNameEnum.byCapitalizedName(name);
            if (headerName == null) {
                return new HeaderNameImpl(Ascii.toLowerCase(name), name);
            }
            return headerName;
        }

        /**
         * Find or create a header name.
         * If a known indexed header exists for the lower case name, the instance is returned.
         * Otherwise a new header name is created with the provided names.
         *
         * @param lowerCase   lower case name
         * @param defaultCase default case to use for custom header names (header names not known by Helidon)
         * @return header name instance
         */
        public static HeaderName create(String lowerCase, String defaultCase) {
            HeaderName headerName = HeaderNameEnum.byName(lowerCase);
            if (headerName == null) {
                return new HeaderNameImpl(lowerCase, defaultCase);
            } else {
                return headerName;
            }
        }

        /**
         * Create a header name from lower case letters.
         *
         * @param lowerCase lower case
         * @return a new header name
         */
        public static HeaderName createFromLowercase(String lowerCase) {
            HeaderName headerName = HeaderNameEnum.byName(lowerCase);
            if (headerName == null) {
                return new HeaderNameImpl(lowerCase, lowerCase);
            } else {
                return headerName;
            }
        }

    }

    /**
     * Values of commonly used headers.
     */
    public static final class Headers {
        /**
         * Accept byte ranges for file download.
         */
        public static final Header ACCEPT_RANGES_BYTES = createCached(HeaderNames.ACCEPT_RANGES, "bytes");
        /**
         * Not accepting byte ranges for file download.
         */
        public static final Header ACCEPT_RANGES_NONE = createCached(HeaderNames.ACCEPT_RANGES, "none");
        /**
         * Chunked transfer encoding.
         * Used in {@code HTTP/1}.
         */
        public static final Header TRANSFER_ENCODING_CHUNKED = createCached(HeaderNames.TRANSFER_ENCODING, "chunked");
        /**
         * Connection keep-alive.
         * Used in {@code HTTP/1}.
         */
        public static final Header CONNECTION_KEEP_ALIVE = createCached(HeaderNames.CONNECTION, "keep-alive");
        /**
         * Connection close.
         * Used in {@code HTTP/1}.
         */
        public static final Header CONNECTION_CLOSE = createCached(HeaderNames.CONNECTION, "close");
        /**
         * Content type application/json with no charset.
         */
        public static final Header CONTENT_TYPE_JSON = createCached(HeaderNames.CONTENT_TYPE, "application/json");
        /**
         * Content type text plain with no charset.
         */
        public static final Header CONTENT_TYPE_TEXT_PLAIN = createCached(HeaderNames.CONTENT_TYPE, "text/plain");
        /**
         * Content type octet stream.
         */
        public static final Header CONTENT_TYPE_OCTET_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                                        "application/octet-stream");
        /**
         * Content type SSE event stream.
         */
        public static final Header CONTENT_TYPE_EVENT_STREAM = createCached(HeaderNames.CONTENT_TYPE,
                                                                                        "text/event-stream");

        /**
         * Accept application/json.
         */
        public static final Header ACCEPT_JSON = createCached(HeaderNames.ACCEPT, "application/json");
        /**
         * Accept text/plain with UTF-8.
         */
        public static final Header ACCEPT_TEXT = createCached(HeaderNames.ACCEPT, "text/plain;charset=UTF-8");
        /**
         * Accept text/event-stream.
         */
        public static final Header ACCEPT_EVENT_STREAM = createCached(HeaderNames.ACCEPT, "text/event-stream");
        /**
         * Expect 100 header.
         */
        public static final Header EXPECT_100 = createCached(HeaderNames.EXPECT, "100-continue");
        /**
         * Content length with 0 value.
         */
        public static final Header CONTENT_LENGTH_ZERO = createCached(HeaderNames.CONTENT_LENGTH, "0");
        /**
         * Cache control without any caching.
         */
        public static final Header CACHE_NO_CACHE = create(HeaderNames.CACHE_CONTROL, "no-cache",
                                                                       "no-store",
                                                                       "must-revalidate",
                                                                       "no-transform");
        /**
         * Cache control that allows caching with no transform.
         */
        public static final Header CACHE_NORMAL = createCached(HeaderNames.CACHE_CONTROL, "no-transform");

        /**
         * TE header set to {@code trailers}, used to enable trailer headers.
         */
        public static final Header TE_TRAILERS = createCached(HeaderNames.TE, "trailers");

        private Headers() {
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, String value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, int value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(String name, long value) {
            return createCached(HeaderNames.create(name), value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, String value) {
            return new HeaderValueCached(name, false,
                                         false,
                                         value.getBytes(StandardCharsets.US_ASCII),
                                         value);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, int value) {
            return createCached(name, String.valueOf(value));
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name  header name
         * @param value value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, long value) {
            return createCached(name, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value lazy string with the value
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, LazyString value) {
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);

            return new HeaderValueLazy(name, false, false, value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value integer value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, int value) {
            Objects.requireNonNull(name);

            return new HeaderValueSingle(name, false, false, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value long value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, long value) {
            Objects.requireNonNull(name);

            return new HeaderValueSingle(name, false, false, String.valueOf(value));
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, String value) {
            Objects.requireNonNull(name, "HeaderName must not be null");
            Objects.requireNonNull(value, "HeaderValue must not be null");

            return new HeaderValueSingle(name,
                                         false,
                                         false,
                                         value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, String value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, int value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header with a single value. This header is considered unchanging and not sensitive.
         *
         * @param name  name of the header
         * @param value value of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, long value) {
            Objects.requireNonNull(name, "Header name must not be null");

            return create(HeaderNames.create(name), value);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, String... values) {
            if (values.length == 0) {
                throw new IllegalArgumentException("Cannot create a header without a value. Header: " + name);
            }
            return new HeaderValueArray(name, false, false, values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, String... values) {
            return create(HeaderNames.create(name), values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(HeaderName name, Collection<String> values) {
            return new HeaderValueList(name, false, false, values);
        }

        /**
         * Create a new header. This header is considered unchanging and not sensitive.
         *
         * @param name   name of the header
         * @param values values of the header
         * @return a new header
         * @see #create(io.helidon.http.Http.HeaderName, boolean, boolean, String...)
         */
        public static Header create(String name, Collection<String> values) {
            return create(HeaderNames.create(name), values);
        }

        /**
         * Create and cache byte value.
         * Use this method if the header value is stored in a constant, or used repeatedly.
         *
         * @param name      header name
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header createCached(HeaderName name, boolean changing, boolean sensitive, String value) {
            return new HeaderValueCached(name, changing, sensitive, value.getBytes(StandardCharsets.UTF_8), value);
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param values    value(s) of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, String... values) {
            return new HeaderValueArray(name, changing, sensitive, values);
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, int value) {
            return create(name, changing, sensitive, String.valueOf(value));
        }

        /**
         * Create a new header.
         *
         * @param name      name of the header
         * @param changing  whether the value is changing often (to disable caching for HTTP/2)
         * @param sensitive whether the value is sensitive (to disable caching for HTTP/2)
         * @param value     value of the header
         * @return a new header
         */
        public static Header create(HeaderName name, boolean changing, boolean sensitive, long value) {
            return create(name, changing, sensitive, String.valueOf(value));
        }
    }

    /**
     * Support for HTTP date formats based on <a href="https://tools.ietf.org/html/rfc2616">RFC2616</a>.
     */
    public static final class DateTime {
        /**
         * The RFC850 date-time formatter, such as {@code 'Sunday, 06-Nov-94 08:49:37 GMT'}.
         * <p>
         * This is <b>obsolete</b> standard (obsoleted by RFC1036). Headers MUST NOT be generated in this format.
         * However it should be used as a fallback for parsing to achieve compatibility with older HTTP standards.
         * <p>
         * Since the format accepts <b>2 digits year</b> representation formatter works well for dates between
         * {@code (now - 50 Years)} and {@code (now + 49 Years)}.
         */
        public static final DateTimeFormatter RFC_850_DATE_TIME = DateTimeHelper.RFC_850_DATE_TIME;
        /**
         * The RFC1123 date-time formatter, such as {@code 'Tue, 3 Jun 2008 11:05:30 GMT'}.
         * <p>
         * <b>This is standard for RFC2616 and all created headers MUST be in this format!</b> However implementation must
         * accept headers also in RFC850 and <i>ANSI C</i> {@code asctime()} format.
         * <p>
         * This is just copy of convenient copy of {@link java.time.format.DateTimeFormatter#RFC_1123_DATE_TIME}.
         */
        public static final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;
        /**
         * The <i>ANSI C's</i> {@code asctime()} format, such as {@code 'Sun Nov  6 08:49:37 1994'}.
         * <p>
         * Headers MUST NOT be generated in this format.
         * However it should be used as a fallback for parsing to achieve compatibility with older HTTP standards.
         */
        public static final DateTimeFormatter ASCTIME_DATE_TIME = DateTimeHelper.ASCTIME_DATE_TIME;

        private DateTime() {
        }

        /**
         * Parse provided text to {@link java.time.ZonedDateTime} using any possible date / time format specified
         * by <a href="https://tools.ietf.org/html/rfc2616">RFC2616 Hypertext Transfer Protocol</a>.
         * <p>
         * Formats are specified by {@link #RFC_1123_DATE_TIME}, {@link #RFC_850_DATE_TIME} and {@link #ASCTIME_DATE_TIME}.
         *
         * @param text a text to parse.
         * @return parsed date time.
         * @throws java.time.format.DateTimeParseException if not in any of supported formats.
         */
        public static ZonedDateTime parse(String text) {
            return DateTimeHelper.parse(text);
        }

        /**
         * Last recorded timestamp.
         *
         * @return timestamp
         */
        public static ZonedDateTime timestamp() {
            return DateTimeHelper.timestamp();
        }

        /**
         * Get current time as RFC-1123 string.
         *
         * @return formatted current time
         * @see #RFC_1123_DATE_TIME
         */
        public static String rfc1123String() {
            return DateTimeHelper.rfc1123String();
        }

        /**
         * Formatted date time terminated by carriage return and new line.
         *
         * @return date bytes for HTTP/1
         */
        public static byte[] http1Bytes() {
            return DateTimeHelper.http1Bytes();
        }
    }
}
