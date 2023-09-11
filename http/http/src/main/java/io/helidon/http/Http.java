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
     * HTTP Header with {@link HeaderName} and value.
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
         * @see #create(HeaderName, boolean, boolean, String...)
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
