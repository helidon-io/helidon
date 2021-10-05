/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates.
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

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.DAY_OF_WEEK;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.OFFSET_SECONDS;
import static java.time.temporal.ChronoField.SECOND_OF_MINUTE;
import static java.time.temporal.ChronoField.YEAR;

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
     * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10">HTTP/1.1 documentation</a>
     * for the complete list. Additional status codes can be added by applications
     * by creating an implementation of {@link ResponseStatus}.
     * <p>
     * Copied from JAX-RS.
     */
    public enum Status implements ResponseStatus {
        /**
         * 100 Continue,
         * see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.1.1">HTTP/1.1 documentations</a>
         */
        CONTINUE_100(100, "Continue"),
        /**
         * 101 Switching Protocols,
         * see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.1.2">HTTP/1.1 documentations</a>
         */
        SWITCHING_PROTOCOLS_101(101, "Switching Protocols"),
        /**
         * 200 OK, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.1">HTTP/1.1 documentation</a>.
         */
        OK_200(200, "OK"),
        /**
         * 201 Created, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.2">HTTP/1.1 documentation</a>.
         */
        CREATED_201(201, "Created"),
        /**
         * 202 Accepted, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.3">HTTP/1.1 documentation</a>
         * .
         */
        ACCEPTED_202(202, "Accepted"),
        /**
         * 204 No Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.5">HTTP/1.1 documentation</a>.
         */
        NO_CONTENT_204(204, "No Content"),
        /**
         * 205 Reset Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        RESET_CONTENT_205(205, "Reset Content"),
        /**
         * 206 Reset Content, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.2.7">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        PARTIAL_CONTENT_206(206, "Partial Content"),
        /**
         * 301 Moved Permanently, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.2">HTTP/1.1 documentation</a>.
         */
        MOVED_PERMANENTLY_301(301, "Moved Permanently"),
        /**
         * 302 Found, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        FOUND_302(302, "Found"),
        /**
         * 303 See Other, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.4">HTTP/1.1 documentation</a>.
         */
        SEE_OTHER_303(303, "See Other"),
        /**
         * 304 Not Modified, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.5">HTTP/1.1 documentation</a>.
         */
        NOT_MODIFIED_304(304, "Not Modified"),
        /**
         * 305 Use Proxy, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        USE_PROXY_305(305, "Use Proxy"),
        /**
         * 307 Temporary Redirect, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.3.8">HTTP/1.1 documentation</a>.
         */
        TEMPORARY_REDIRECT_307(307, "Temporary Redirect"),
        /**
         * 400 Bad Request, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.1">HTTP/1.1 documentation</a>.
         */
        BAD_REQUEST_400(400, "Bad Request"),
        /**
         * 401 Unauthorized, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.2">HTTP/1.1 documentation</a>.
         */
        UNAUTHORIZED_401(401, "Unauthorized"),
        /**
         * 402 Payment Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        PAYMENT_REQUIRED_402(402, "Payment Required"),
        /**
         * 403 Forbidden, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.4">HTTP/1.1 documentation</a>.
         */
        FORBIDDEN_403(403, "Forbidden"),
        /**
         * 404 Not Found, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.5">HTTP/1.1 documentation</a>.
         */
        NOT_FOUND_404(404, "Not Found"),
        /**
         * 405 Method Not Allowed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        METHOD_NOT_ALLOWED_405(405, "Method Not Allowed"),
        /**
         * 406 Not Acceptable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.7">HTTP/1.1 documentation</a>.
         */
        NOT_ACCEPTABLE_406(406, "Not Acceptable"),
        /**
         * 407 Proxy Authentication Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.8">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        PROXY_AUTHENTICATION_REQUIRED_407(407, "Proxy Authentication Required"),
        /**
         * 408 Request Timeout, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.9">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        REQUEST_TIMEOUT_408(408, "Request Timeout"),
        /**
         * 409 Conflict, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.10">HTTP/1.1 documentation</a>.
         */
        CONFLICT_409(409, "Conflict"),
        /**
         * 410 Gone, see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.11">HTTP/1.1 documentation</a>.
         */
        GONE_410(410, "Gone"),
        /**
         * 411 Length Required, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.12">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        LENGTH_REQUIRED_411(411, "Length Required"),
        /**
         * 412 Precondition Failed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.13">HTTP/1.1 documentation</a>.
         */
        PRECONDITION_FAILED_412(412, "Precondition Failed"),
        /**
         * 413 Request Entity Too Large, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.14">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        REQUEST_ENTITY_TOO_LARGE_413(413, "Request Entity Too Large"),
        /**
         * 414 Request-URI Too Long, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.15">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        REQUEST_URI_TOO_LONG_414(414, "Request-URI Too Long"),
        /**
         * 415 Unsupported Media Type, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.16">HTTP/1.1 documentation</a>.
         */
        UNSUPPORTED_MEDIA_TYPE_415(415, "Unsupported Media Type"),
        /**
         * 416 Requested Range Not Satisfiable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.17">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        REQUESTED_RANGE_NOT_SATISFIABLE_416(416, "Requested Range Not Satisfiable"),
        /**
         * 417 Expectation Failed, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.4.18">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        EXPECTATION_FAILED_417(417, "Expectation Failed"),
        /**
         * 418 I'm a teapot, see
         * <a href="https://tools.ietf.org/html/rfc2324#section-2.3.2">Hyper Text Coffee Pot Control Protocol (HTCPCP/1.0)</a>.
         */
        I_AM_A_TEAPOT(418, "I'm a teapot"),
        /**
         * 500 Internal Server Error, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.1">HTTP/1.1 documentation</a>.
         */
        INTERNAL_SERVER_ERROR_500(500, "Internal Server Error"),
        /**
         * 501 Not Implemented, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.2">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        NOT_IMPLEMENTED_501(501, "Not Implemented"),
        /**
         * 502 Bad Gateway, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.3">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        BAD_GATEWAY_502(502, "Bad Gateway"),
        /**
         * 503 Service Unavailable, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.4">HTTP/1.1 documentation</a>.
         */
        SERVICE_UNAVAILABLE_503(503, "Service Unavailable"),
        /**
         * 504 Gateway Timeout, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.5">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        GATEWAY_TIMEOUT_504(504, "Gateway Timeout"),
        /**
         * 505 HTTP Version Not Supported, see
         * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html#sec10.5.6">HTTP/1.1 documentation</a>.
         *
         * @since 2.0
         */
        HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");

        private final int code;
        private final String reason;
        private final Family family;

        Status(int statusCode, String reasonPhrase) {
            this.code = statusCode;
            this.reason = reasonPhrase;
            this.family = Family.of(statusCode);
        }

        /**
         * Convert a numerical status code into the corresponding {@code Status} enum value.
         * <p>
         * As opposed to {@link ResponseStatus#create(int)}, this method returns {@link Optional#empty() no value}
         * for an unknown status code not represented by a value in this enumeration of standard HTTP response status codes.
         *
         * @param statusCode the numerical status code.
         * @return optionally the matching Status if a matching {@code Status} value is defined.
         */
        public static Optional<Status> find(int statusCode) {
            for (Status s : Status.values()) {
                if (s.code == statusCode) {
                    return Optional.of(s);
                }
            }
            return Optional.empty();
        }

        /**
         * Get the class of status code.
         *
         * @return the class of status code.
         */
        @Override
        public Family family() {
            return family;
        }

        /**
         * Get the associated status code.
         *
         * @return the status code.
         */
        @Override
        public int code() {
            return code;
        }

        /**
         * Get the reason phrase.
         *
         * @return the reason phrase.
         */
        @Override
        public String reasonPhrase() {
            return reason;
        }

        /**
         * Get the response status as string.
         *
         * @return the response status string in the form of a partial HTTP response status line,
         * i.e. {@code "Status-Code SP Reason-Phrase"}.
         */
        @Override
        public String toString() {
            return code + " " + reason;
        }
    }

    /**
     * Enumeration of all standard HTTP {@link RequestMethod methods}.
     * It is extensible enumeration pattern.
     *
     * @see RequestMethod
     */
    public enum Method implements RequestMethod {
        /**
         * The OPTIONS method represents a request for information about the communication options available
         * on the request/response chain identified by the Request-URI. This method allows the client to determine the options
         * and/or requirements  associated with a resource, or the capabilities of a server, without implying a resource action
         * or initiating a resource retrieval.
         */
        OPTIONS,

        /**
         * The GET method means retrieve whatever information (in the form of an entity) is identified by the Request-URI.
         * If the Request-URI refers to a data-producing process, it is the produced data which shall be returned as the entity
         * in the response and not the source text of the process, unless that text happens to be the output of the tryProcess.
         */
        GET,

        /**
         * The HEAD method is identical to {@link #GET} except that the server MUST NOT return a message-body in the response.
         * The metainformation contained in the HTTP headers in response to a HEAD request SHOULD be identical to the information
         * sent in response to a GET request. This method can be used for obtaining metainformation about the entity implied
         * by the request without transferring the entity-body itself. This method is often used for testing hypertext links
         * for validity, accessibility, and recent modification.
         */
        HEAD,

        /**
         * The POST method is used to request that the origin server acceptedTypes the entity enclosed in the request
         * as a new subordinate of the resource identified by the Request-URI in the Request-Line.
         * The actual function performed by the POST method is determined by the server and is usually dependent on the
         * Request-URI. The posted entity is subordinate to that URI in the same way that a file is subordinate to a directory
         * containing it, a news article is subordinate to a newsgroup to which it is posted, or a record is subordinate
         * to a database.
         */
        POST,

        /**
         * The PUT method requests that the enclosed entity be stored under the supplied Request-URI. If the Request-URI refers
         * to an already existing resource, the enclosed entity SHOULD be considered as a modified version of the one residing
         * on the origin server. If the Request-URI does not point to an existing resource, and that URI is capable of being
         * defined as a new resource by the requesting user agent, the origin server can create the resource with that URI.
         * If a new resource is created, the origin server MUST inform the user agent via the 201 (Created) response.
         * If an existing resource is modified, either the 200 (OK) or 204 (No Content) response codes SHOULD be sent to indicate
         * successful completion of the request. If the resource could not be created or modified with the Request-URI,
         * an appropriate error response SHOULD be given that reflects the nature of the problem. The recipient of the entity
         * MUST NOT ignore any Content-* (e.g. Content-Range) headers that it does not understand or implement and MUST return
         * a 501 (Not Implemented) response in such cases.
         */
        PUT,

        /**
         * The PATCH method as described in RFC 5789 is used to perform an update to an existing resource, where the request
         * payload only has to contain the instructions on how to perform the update. This is in contrast to PUT which
         * requires that the payload contains the new version of the resource.
         * If an existing resource is modified, either the 200 (OK) or 204 (No Content) response codes SHOULD be sent to indicate
         * successful completion of the request.
         */
        PATCH,

        /**
         * The DELETE method requests that the origin server delete the resource identified by the Request-URI.
         * This method MAY be overridden by human intervention (or other means) on the origin server. The client cannot
         * be guaranteed that the operation has been carried out, even if the status code returned from the origin server
         * indicates that the action has been completed successfully. However, the server SHOULD NOT indicate success unless,
         * at the time the response is given, it intends to delete the resource or move it to an inaccessible location.
         */
        DELETE,

        /**
         * The TRACE method is used to invoke a remote, application-layer loop- back of the request message.
         * The final recipient of the request SHOULD reflect the message received back to the client as the entity-body
         * of a 200 (OK) response. The final recipient is either the origin server or the first proxy or gateway to receive
         * a Max-Forwards value of zero (0) in the request (see section 14.31). A TRACE request MUST NOT include an entity.
         */
        TRACE

    }

    /**
     * Enumeration of supported HTTP protocol versions.
     */
    public enum Version {

        /**
         * HTTP version {@code HTTP/1.0}.
         */
        V1_0("HTTP/1.0"),

        /**
         * HTTP version {@code HTTP/1.1}.
         */
        V1_1("HTTP/1.1"),

        /**
         * HTTP version {@code HTTP/2.0}.
         */
        V2_0("HTTP/2.0");

        private final String value;

        Version(String value) {
            this.value = value;
        }

        /**
         * Returns HTTP version for provided parameter.
         *
         * @param version HTTP version.
         * @return Version instance.
         * @throws NullPointerException     if parameter {@code version} is null.
         * @throws IllegalArgumentException if it is not provided version.
         */
        public static Version create(String version) {
            Objects.requireNonNull(version, "Version value is null!");
            for (Version v : Version.values()) {
                if (version.equals(v.value)) {
                    return v;
                }
            }
            throw new IllegalArgumentException("Unknown HTTP version: " + version + "!");
        }

        /**
         * Returns {@code String} representation of this {@link Version}.
         *
         * @return a string representation.
         */
        public String value() {
            return value;
        }
    }

    /**
     * Interface representing an HTTP request method, all standard methods are in {@link Method} enumeration.
     *
     * @see Method
     */
    @FunctionalInterface
    public interface RequestMethod {

        /**
         * Create new HTTP request method instance from the provided name.
         * <p>
         * In case the method name is recognized as one of the {@link Method standard HTTP methods}, the respective enumeration
         * value is returned.
         *
         * @param name the method name. Must not be {@code null} or empty and must be a legal HTTP method name string.
         * @return HTTP request method instance representing an HTTP method with the provided name.
         * @throws IllegalArgumentException In case of illegal method name or in case the name is empty or {@code null}.
         */
        static RequestMethod create(String name) {
            if (name != null && !name.isEmpty()) {
                for (int i = 0; i < name.length(); i++) {
                    char ch = name.charAt(i);
                    if (Character.isISOControl(ch)) {
                        throw new IllegalArgumentException("HTTP method name parameter must not contain ISO control characters!");
                    } else if (Character.isWhitespace(ch)) {
                        throw new IllegalArgumentException("HTTP method name parameter must not contain whitespace!");
                    }
                }
            } else {
                throw new IllegalArgumentException("HTTP method name must not be null or empty!");
            }

            final String upperCaseName = name.toUpperCase();

            for (Method method : Method.values()) {
                if (method.name().equals(upperCaseName)) {
                    return method;
                }
            }

            return new RequestMethod() {
                @Override
                public String name() {
                    return upperCaseName;
                }

                @Override
                public boolean equals(Object other) {
                    return (other instanceof RequestMethod) && name().equals(((RequestMethod) other).name());
                }

                @Override
                public int hashCode() {
                    return upperCaseName.hashCode();
                }
            };
        }

        /**
         * Get method name.
         *
         * @return a method name.
         */
        String name();
    }

    /**
     * Base interface for status codes used in HTTP responses.
     * <p>
     * Copied from JAX-RS.
     */
    @SuppressWarnings("unused")
    public interface ResponseStatus {

        /**
         * Convert a numerical status code into the corresponding ResponseStatus.
         * <p>
         * As opposed to {@link Status#find(int)}, this method is guaranteed to always return an instance.
         * For an unknown {@link Status} it creates an ad-hoc {@link ResponseStatus}.
         *
         * @param statusCode the numerical status code
         * @return the matching ResponseStatus; either a {@link Status} or an ad-hoc {@link ResponseStatus}
         */
        static ResponseStatus create(int statusCode) {
            return create(statusCode, null);
        }

        /**
         * Convert a numerical status code into the corresponding ResponseStatus.
         * <p>
         * It either returns an existing {@link Status} if possible. For an unknown {@link Status} it creates
         * an ad-hoc {@link ResponseStatus} or whenever a custom reason phrase is provided.
         *
         * @param statusCode   the numerical status code; if known, a {@link Status is returned}
         * @param reasonPhrase the reason phrase; if {@code null} or a known reason phrase, a
         *                     {@link Status} is returned; otherwise, a new instance is returned
         * @return the matching ResponseStatus; either a {@link Status} or an ad-hoc {@link ResponseStatus}
         */
        static ResponseStatus create(int statusCode, String reasonPhrase) {
            return Status.find(statusCode)
                    // keep status that has the same reason phrase or if the supplied phrase is null
                    .filter(status -> (null == reasonPhrase) || status.reasonPhrase().equalsIgnoreCase(reasonPhrase))
                    // the next statement returns an instance of ResponseStatus, previous of Status - cast
                    // to make the mapping work without <? extends>
                    .map(ResponseStatus.class::cast)
                    // only create the new ResponseStatus if we did not find an existing Status
                    .orElseGet(() -> new ResponseStatus() {
                        // not using a method, as it would be implicitly public (this being an interface)
                        @Override
                        public int code() {
                            return statusCode;
                        }

                        @Override
                        public Family family() {
                            return Family.of(statusCode);
                        }

                        @Override
                        public String reasonPhrase() {
                            return reasonPhrase;
                        }

                        @Override
                        public int hashCode() {
                            return Integer.hashCode(statusCode);
                        }

                        @Override
                        public boolean equals(Object other) {
                            if (other instanceof ResponseStatus) {
                                ResponseStatus os = (ResponseStatus) other;

                                return (code() == os.code())
                                        && (family() == os.family())
                                        && (reasonPhraseEquals(os));
                            }

                            return false;
                        }

                        private boolean reasonPhraseEquals(ResponseStatus other) {
                            if (null == reasonPhrase) {
                                return null == other.reasonPhrase();
                            }

                            return reasonPhrase.equalsIgnoreCase(other.reasonPhrase());
                        }

                        @Override
                        public String toString() {
                            return "ResponseStatus{code=" + code()
                                    + ", reason=" + reasonPhrase()
                                    + "}";
                        }
                    });
        }

        /**
         * Get the associated integer value representing the status code.
         *
         * @return the integer value representing the status code.
         */
        int code();

        /**
         * Get the class of status code.
         *
         * @return the class of status code.
         */
        Family family();

        /**
         * Get the reason phrase.
         *
         * @return the reason phrase.
         */
        String reasonPhrase();

        /**
         * An enumeration representing the class of status code. Family is used
         * here since class is overloaded in Java.
         * <p>
         * Copied from JAX-RS.
         */
        enum Family {

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
                switch (statusCode / 100) {
                case 1:
                    return Family.INFORMATIONAL;
                case 2:
                    return Family.SUCCESSFUL;
                case 3:
                    return Family.REDIRECTION;
                case 4:
                    return Family.CLIENT_ERROR;
                case 5:
                    return Family.SERVER_ERROR;
                default:
                    return Family.OTHER;
                }
            }
        }
    }

    /**
     * Utility class with a list of names of standard HTTP headers and related tooling methods.
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static final class Header {

        /**
         * The <code>{@value}</code> header name.
         * Content-Types that are acceptedTypes for the response.
         */
        public static final String ACCEPT = "Accept";
        /**
         * The <code>{@value}</code> header name.
         * Character sets that are acceptedTypes.
         */
        public static final String ACCEPT_CHARSET = "Accept-Charset";
        /**
         * The <code>{@value}</code> header name.
         * List of acceptedTypes encodings.
         */
        public static final String ACCEPT_ENCODING = "Accept-Encoding";
        /**
         * The <code>{@value}</code> header name.
         * List of acceptedTypes human languages for response.
         */
        public static final String ACCEPT_LANGUAGE = "Accept-Language";
        /**
         * The <code>{@value}</code> header name.
         * Acceptable version in time.
         */
        public static final String ACCEPT_DATETIME = "Accept-Datetime";
        /**
         * The <code>{@value}</code> header name.
         * Authentication credentials for HTTP authentication.
         */
        public static final String AUTHORIZATION = "Authorization";
        /**
         * The <code>{@value}</code> header name.
         * An HTTP cookie previously sent by the server with {@value SET_COOKIE}.
         */
        public static final String COOKIE = "Cookie";
        /**
         * The <code>{@value}</code> header name.
         * Indicates that particular server behaviors are required by the client.
         */
        public static final String EXPECT = "Expect";
        /**
         * The <code>{@value}</code> header name.
         * Disclose original information of a client connecting to a web server through an HTTP proxy.
         */
        public static final String FORWARDED = "Forwarded";
        /**
         * The <code>{@value}</code> header name.
         * The email address of the user making the request.
         */
        public static final String FROM = "From";
        /**
         * The <code>{@value}</code> header name.
         * The domain name of the server (for virtual hosting), and the TCP port number on which the server is listening.
         * The port number may be omitted if the port is the standard port for the service requested.
         */
        public static final String HOST = "Host";
        /**
         * The <code>{@value}</code> header name.
         * Only perform the action if the client supplied entity matches the same entity on the server. This is mainly
         * for methods like PUT to only update a resource if it has not been modified since the user last updated it.
         */
        public static final String IF_MATCH = "If-Match";
        /**
         * The <code>{@value}</code> header name.
         * Allows a 304 Not Modified to be returned if content is unchanged.
         */
        public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
        /**
         * The <code>{@value}</code> header name.
         * Allows a 304 Not Modified to be returned if content is unchanged, based on {@link #ETAG}.
         */
        public static final String IF_NONE_MATCH = "If-None-Match";
        /**
         * The <code>{@value}</code> header name.
         * If the entity is unchanged, send me the part(s) that I am missing; otherwise, send me the entire new entity.
         */
        public static final String IF_RANGE = "If-Range";
        /**
         * The <code>{@value}</code> header name.
         * Only send the response if the entity has not been modified since a specific time.
         */
        public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
        /**
         * The <code>{@value}</code> header name.
         * Limit the number of times the message can be forwarded through proxies or gateways.
         */
        public static final String MAX_FORWARDS = "Max-Forwards";
        /**
         * The <code>{@value}</code> header name.
         * Initiates a request for cross-origin resource sharing (asks server for an {@code 'Access-Control-Allow-Origin'}
         * response field).
         */
        public static final String ORIGIN = "Origin";
        /**
         * The <code>{@value}</code> header name.
         * Request only part of an entity. Bytes are numbered from 0.
         */
        public static final String RANGE = "Range";
        /**
         * The <code>{@value}</code> header name.
         * This is the address of the previous web page from which a link to the currently requested page was followed.
         * (The word <i>referrer</i> has been misspelled in the RFC as well as in most implementations to the point that it has
         * become standard usage and is considered correct terminology.)
         */
        public static final String REFERER = "Referer";
        /**
         * The <code>{@value}</code> header name.
         * The transfer encodings the user agent is willing to acceptedTypes: the same values as for the response header field
         * {@code Transfer-Encoding} can be used, plus the <i>trailers</i> value (related to the <i>chunked</i> transfer method)
         * to notify the server it expects to receive additional fields in the trailer after the last, zero-sized, chunk.
         */
        public static final String TE = "TE";
        /**
         * The <code>{@value}</code> header name.
         * The user agent string of the user agent.
         */
        public static final String USER_AGENT = "User-Agent";
        /**
         * The <code>{@value}</code> header name.
         * Informs the server of proxies through which the request was sent.
         */
        public static final String VIA = "Via";
        /**
         * The <code>{@value}</code> header name.
         * Specifies which patch document formats this server supports.
         */
        public static final String ACCEPT_PATCH = "Accept-Patch";
        /**
         * The <code>{@value}</code> header name.
         * What partial content range types this server supports via byte serving.
         */
        public static final String ACCEPT_RANGES = "Accept-Ranges";
        /**
         * The <code>{@value}</code> header name.
         * The age the object has been in a proxy cache in seconds.
         */
        public static final String AGE = "Age";
        /**
         * The <code>{@value}</code> header name.
         * Valid actions for a specified resource. To be used for a 405 Method not allowed.
         */
        public static final String ALLOW = "Allow";
        /**
         * The <code>{@value}</code> header name.
         * A server uses <i>Alt-Svc</i> header (meaning Alternative Services) to indicate that its resources can also be
         * accessed at a different network location (host or port) or using a different protocol.
         */
        public static final String ALT_SVC = "Alt-Svc";
        /**
         * The <code>{@value}</code> header name.
         * Tells all caching mechanisms from server to client whether they may cache this object. It is measured in seconds.
         */
        public static final String CACHE_CONTROL = "Cache-Control";
        /**
         * The <code>{@value}</code> header name.
         * Control options for the current connection and list of hop-by-hop response fields.
         */
        public static final String CONNECTION = "Connection";
        /**
         * The <code>{@value}</code> header name.
         * An opportunity to raise a <i>File Download</i> dialogue box for a known MIME type with binary format or suggest
         * a filename for dynamic content. Quotes are necessary with special characters.
         */
        public static final String CONTENT_DISPOSITION = "Content-Disposition";
        /**
         * The <code>{@value}</code> header name.
         * The type of encoding used on the data.
         */
        public static final String CONTENT_ENCODING = "Content-Encoding";
        /**
         * The <code>{@value}</code> header name.
         * The natural language or languages of the intended audience for the enclosed content.
         */
        public static final String CONTENT_LANGUAGE = "Content-Language";
        /**
         * The <code>{@value}</code> header name.
         * The length of the response body in octets.
         */
        public static final String CONTENT_LENGTH = "Content-Length";
        /**
         * The <code>{@value}</code> header name.
         * An alternate location for the returned data.
         */
        public static final String CONTENT_LOCATION = "aa";
        /**
         * The <code>{@value}</code> header name.
         * Where in a full body message this partial message belongs.
         */
        public static final String CONTENT_RANGE = "Content-Range";
        /**
         * The <code>{@value}</code> header name.
         * The MIME type of this content.
         */
        public static final String CONTENT_TYPE = "Content-Type";
        /**
         * The <code>{@value}</code> header name.
         * The date and time that the message was sent (in <i>HTTP-date</i> format as defined by RFC 7231).
         */
        public static final String DATE = "Date";
        /**
         * The <code>{@value}</code> header name.
         * An identifier for a specific version of a resource, often a message digest.
         */
        public static final String ETAG = "ETag";
        /**
         * The <code>{@value}</code> header name.
         * Gives the date/time after which the response is considered stale (in <i>HTTP-date</i> format as defined by RFC 7231)
         */
        public static final String EXPIRES = "Expires";
        /**
         * The <code>{@value}</code> header name.
         * The last modified date for the requested object (in <i>HTTP-date</i> format as defined by RFC 7231)
         */
        public static final String LAST_MODIFIED = "Last-Modified";
        /**
         * The <code>{@value}</code> header name.
         * Used to express a typed relationship with another resource, where the relation type is defined by RFC 5988.
         */
        public static final String LINK = "Link";
        /**
         * The <code>{@value}</code> header name.
         * Used in redirection, or whenRequest a new resource has been created.
         */
        public static final String LOCATION = "Location";
        /**
         * The <code>{@value}</code> header name.
         * Implementation-specific fields that may have various effects anywhere along the request-response chain.
         */
        public static final String PRAGMA = "Pragma";
        /**
         * The <code>{@value}</code> header name.
         * HTTP Public Key Pinning, announces hash of website's authentic TLS certificate.
         */
        public static final String PUBLIC_KEY_PINS = "Public-Key-Pins";
        /**
         * The <code>{@value}</code> header name.
         * If an entity is temporarily unavailable, this instructs the client to try again later. Value could be a specified
         * period of time (in seconds) or an HTTP-date.
         */
        public static final String RETRY_AFTER = "Retry-After";
        /**
         * The <code>{@value}</code> header name.
         * A name for the server.
         */
        public static final String SERVER = "Server";
        /**
         * The <code>{@value}</code> header name.
         * An HTTP cookie set directive.
         */
        public static final String SET_COOKIE = "Set-Cookie";
        /**
         * The <code>{@value}</code> header name.
         * A HSTS Policy informing the HTTP client how long to cache the HTTPS only policy and whether this applies to subdomains.
         */
        public static final String STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
        /**
         * The <code>{@value}</code> header name.
         * The Trailer general field value indicates that the given set of header fields is present in the trailer of
         * a message encoded with chunked transfer coding.
         */
        public static final String TRAILER = "Trailer";
        /**
         * The <code>{@value}</code> header name.
         * The form of encoding used to safely transfer the entity to the user. Currently defined methods are:
         * {@code chunked, compress, deflate, gzip, identity}.
         */
        public static final String TRANSFER_ENCODING = "Transfer-Encoding";
        /**
         * The <code>{@value}</code> header name.
         * Tracking Status Value, value suggested to be sent in response to a DNT(do-not-track).
         */
        public static final String TSV = "TSV";
        /**
         * The <code>{@value}</code> header name.
         * Ask to upgrade to another protocol.
         */
        public static final String UPGRADE = "Upgrade";
        /**
         * The <code>{@value}</code> header name.
         * Tells downstream proxies how to match future request headers to decide whether the cached response can be used rather
         * than requesting a fresh one from the origin server.
         */
        public static final String VARY = "Vary";
        /**
         * The <code>{@value}</code> header name.
         * A general warning about possible problems with the entity body.
         */
        public static final String WARNING = "Warning";
        /**
         * The <code>{@value}</code> header name.
         * Indicates the authentication scheme that should be used to access the requested entity.
         */
        public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
        /**
         * The <code>{@value}</code> header name.
         * Corresponds to the certificate CN subject value when client authentication enabled.
         * This header will be removed if it is part of the request.
         */
        public static final String X_HELIDON_CN = "X-HELIDON-CN";

        private Header() {
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
        public static final DateTimeFormatter RFC_850_DATE_TIME;
        /**
         * The RFC1123 date-time formatter, such as {@code 'Tue, 3 Jun 2008 11:05:30 GMT'}.
         * <p>
         * <b>This is standard for RFC2616 and all created headers MUST be in this format!</b> However implementation must
         * accept headers also in RFC850 and <i>ANSI C</i> {@code asctime()} format.
         * <p>
         * This is just copy of convenient copy of {@link DateTimeFormatter#RFC_1123_DATE_TIME}.
         */
        public static final DateTimeFormatter RFC_1123_DATE_TIME = DateTimeFormatter.RFC_1123_DATE_TIME;
        /**
         * The <i>ANSI C's</i> {@code asctime()} format, such as {@code 'Sun Nov  6 08:49:37 1994'}.
         * <p>
         * Headers MUST NOT be generated in this format.
         * However it should be used as a fallback for parsing to achieve compatibility with older HTTP standards.
         */
        public static final DateTimeFormatter ASCTIME_DATE_TIME;
        /**
         * Manual list ensures that no localisation can affect standard parsing/generating.
         */
        private static final Map<Long, String> MONTH_NAME_3D;

        static {
            Map<Long, String> map = new HashMap<>();
            map.put(1L, "Jan");
            map.put(2L, "Feb");
            map.put(3L, "Mar");
            map.put(4L, "Apr");
            map.put(5L, "May");
            map.put(6L, "Jun");
            map.put(7L, "Jul");
            map.put(8L, "Aug");
            map.put(9L, "Sep");
            map.put(10L, "Oct");
            map.put(11L, "Nov");
            map.put(12L, "Dec");
            MONTH_NAME_3D = Collections.unmodifiableMap(map);

            // manually code maps to ensure correct data always used
            // (locale data can be changed by application code)
            Map<Long, String> dayOfWeekFull = Map.of(1L, "Monday",
                                                                      2L, "Tuesday",
                                                                      3L, "Wednesday",
                                                                      4L, "Thursday",
                                                                      5L, "Friday",
                                                                      6L, "Saturday",
                                                                      7L, "Sunday");
            RFC_850_DATE_TIME = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .optionalStart()
                    .appendText(DAY_OF_WEEK, dayOfWeekFull)
                    .appendLiteral(", ")
                    .optionalEnd()
                    .appendValue(DAY_OF_MONTH, 2, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral('-')
                    .appendText(MONTH_OF_YEAR, MONTH_NAME_3D)
                    .appendLiteral('-')
                    .appendValueReduced(YEAR, 2, 2, LocalDate.now().minusYears(50).getYear())
                    .appendLiteral(' ')
                    .appendValue(HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 2)
                    .optionalStart()
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .optionalEnd()
                    .appendLiteral(' ')
                    .appendOffset("+HHMM", "GMT")
                    .toFormatter();

            // manually code maps to ensure correct data always used
            // (locale data can be changed by application code)
            Map<Long, String> dayOfWeek3d = Map.of(1L, "Mon",
                                                                    2L, "Tue",
                                                                    3L, "Wed",
                                                                    4L, "Thu",
                                                                    5L, "Fri",
                                                                    6L, "Sat",
                                                                    7L, "Sun");
            ASCTIME_DATE_TIME = new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .parseLenient()
                    .optionalStart()
                    .appendText(DAY_OF_WEEK, dayOfWeek3d)
                    .appendLiteral(' ')
                    .appendText(MONTH_OF_YEAR, MONTH_NAME_3D)
                    .appendLiteral(' ')
                    .padNext(2)
                    .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
                    .appendLiteral(' ')
                    .appendValue(HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(MINUTE_OF_HOUR, 2)
                    .appendLiteral(':')
                    .appendValue(SECOND_OF_MINUTE, 2)
                    .appendLiteral(' ')
                    .appendValue(YEAR, 4)
                    .parseDefaulting(OFFSET_SECONDS, 0)
                    .toFormatter();
        }

        private DateTime() {
        }

        /**
         * Parse provided text to {@link ZonedDateTime} using any possible date / time format specified
         * by <a href="https://tools.ietf.org/html/rfc2616">RFC2616 Hypertext Transfer Protocol</a>.
         * <p>
         * Formats are specified by {@link #RFC_1123_DATE_TIME}, {@link #RFC_850_DATE_TIME} and {@link #ASCTIME_DATE_TIME}.
         *
         * @param text a text to parse.
         * @return parsed date time.
         * @throws DateTimeParseException if not in any of supported formats.
         */
        public static ZonedDateTime parse(String text) {
            try {
                return ZonedDateTime.parse(text, RFC_1123_DATE_TIME);
            } catch (DateTimeParseException pe) {
                try {
                    return ZonedDateTime.parse(text, RFC_850_DATE_TIME);
                } catch (DateTimeParseException pe2) {
                    return ZonedDateTime.parse(text, ASCTIME_DATE_TIME);
                }
            }
        }
    }
}
