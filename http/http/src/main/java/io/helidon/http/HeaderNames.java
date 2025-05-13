/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import io.helidon.common.buffers.Ascii;
import io.helidon.http.HeaderNameEnum.Strings;

/**
 * Utility class with a list of names of standard HTTP headers and related tooling methods.
 */
public final class HeaderNames {
    /**
     * The {@value} header name.
     * Content-Types that are acceptedTypes for the response.
     */
    public static final String ACCEPT_NAME = Strings.ACCEPT_NAME;
    /**
     * The {@value #ACCEPT_NAME} header name.
     * Content-Types that are acceptedTypes for the response.
     */
    public static final HeaderName ACCEPT = HeaderNameEnum.ACCEPT;
    /**
     * The {@value} header name.
     * Character sets that are acceptedTypes.
     */
    public static final String ACCEPT_CHARSET_NAME = Strings.ACCEPT_CHARSET_NAME;
    /**
     * The {@value #ACCEPT_CHARSET_NAME} header name.
     * Character sets that are acceptedTypes.
     */
    public static final HeaderName ACCEPT_CHARSET = HeaderNameEnum.ACCEPT_CHARSET;
    /**
     * The {@value} header name.
     * List of acceptedTypes encodings.
     */
    public static final String ACCEPT_ENCODING_NAME = Strings.ACCEPT_ENCODING_NAME;
    /**
     * The {@value #ACCEPT_ENCODING_NAME} header name.
     * List of acceptedTypes encodings.
     */
    public static final HeaderName ACCEPT_ENCODING = HeaderNameEnum.ACCEPT_ENCODING;
    /**
     * The {@value} header name.
     * List of acceptedTypes human languages for response.
     */
    public static final String ACCEPT_LANGUAGE_NAME = Strings.ACCEPT_LANGUAGE_NAME;
    /**
     * The {@value #ACCEPT_LANGUAGE_NAME} header name.
     * List of acceptedTypes human languages for response.
     */
    public static final HeaderName ACCEPT_LANGUAGE = HeaderNameEnum.ACCEPT_LANGUAGE;
    /**
     * The {@value} header name.
     * Acceptable version in time.
     */
    public static final String ACCEPT_DATETIME_NAME = Strings.ACCEPT_DATETIME_NAME;
    /**
     * The {@value #ACCEPT_DATETIME_NAME} header name.
     * Acceptable version in time.
     */
    public static final HeaderName ACCEPT_DATETIME = HeaderNameEnum.ACCEPT_DATETIME;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME = Strings.ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME;
    /**
     * The {@value #ACCESS_CONTROL_ALLOW_CREDENTIALS_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_ALLOW_CREDENTIALS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_CREDENTIALS;
    /**
     * The {@value} header name.
     * CORS configuration
     */
    public static final String ACCESS_CONTROL_ALLOW_HEADERS_NAME = Strings.ACCESS_CONTROL_ALLOW_HEADERS_NAME;
    /**
     * The {@value #ACCESS_CONTROL_ALLOW_HEADERS_NAME} header name.
     * CORS configuration
     */
    public static final HeaderName ACCESS_CONTROL_ALLOW_HEADERS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_HEADERS;
    /**
     * The {@value} header name.
     * CORS configuration
     */
    public static final String ACCESS_CONTROL_ALLOW_METHODS_NAME = Strings.ACCESS_CONTROL_ALLOW_METHODS_NAME;
    /**
     * The {@value #ACCESS_CONTROL_ALLOW_METHODS_NAME} header name.
     * CORS configuration
     */
    public static final HeaderName ACCESS_CONTROL_ALLOW_METHODS = HeaderNameEnum.ACCESS_CONTROL_ALLOW_METHODS;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_ALLOW_ORIGIN_NAME = Strings.ACCESS_CONTROL_ALLOW_ORIGIN_NAME;
    /**
     * The {@value #ACCESS_CONTROL_ALLOW_ORIGIN_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_ALLOW_ORIGIN = HeaderNameEnum.ACCESS_CONTROL_ALLOW_ORIGIN;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_EXPOSE_HEADERS_NAME = Strings.ACCESS_CONTROL_EXPOSE_HEADERS_NAME;
    /**
     * The {@value #ACCESS_CONTROL_EXPOSE_HEADERS_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_EXPOSE_HEADERS = HeaderNameEnum.ACCESS_CONTROL_EXPOSE_HEADERS;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_MAX_AGE_NAME = Strings.ACCESS_CONTROL_MAX_AGE_NAME;
    /**
     * The {@value #ACCESS_CONTROL_MAX_AGE_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_MAX_AGE = HeaderNameEnum.ACCESS_CONTROL_MAX_AGE;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_REQUEST_HEADERS_NAME = Strings.ACCESS_CONTROL_REQUEST_HEADERS_NAME;
    /**
     * The {@value #ACCESS_CONTROL_REQUEST_HEADERS_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_REQUEST_HEADERS = HeaderNameEnum.ACCESS_CONTROL_REQUEST_HEADERS;
    /**
     * The {@value} header name.
     * CORS configuration.
     */
    public static final String ACCESS_CONTROL_REQUEST_METHOD_NAME = Strings.ACCESS_CONTROL_REQUEST_METHOD_NAME;
    /**
     * The {@value #ACCESS_CONTROL_REQUEST_METHOD_NAME} header name.
     * CORS configuration.
     */
    public static final HeaderName ACCESS_CONTROL_REQUEST_METHOD = HeaderNameEnum.ACCESS_CONTROL_REQUEST_METHOD;
    /**
     * The {@value} header name.
     * Authentication credentials for HTTP authentication.
     */
    public static final String AUTHORIZATION_NAME = Strings.AUTHORIZATION_NAME;
    /**
     * The {@value #AUTHORIZATION_NAME} header name.
     * Authentication credentials for HTTP authentication.
     */
    public static final HeaderName AUTHORIZATION = HeaderNameEnum.AUTHORIZATION;
    /**
     * The {@value} header name.
     * An HTTP cookie previously sent by the server with {@value #SET_COOKIE_NAME}.
     */
    public static final String COOKIE_NAME = Strings.COOKIE_NAME;
    /**
     * The {@value #COOKIE_NAME} header name.
     * An HTTP cookie previously sent by the server with {@value #SET_COOKIE_NAME}.
     */
    public static final HeaderName COOKIE = HeaderNameEnum.COOKIE;
    /**
     * The {@value} header name.
     * Indicates that particular server behaviors are required by the client.
     */
    public static final String EXPECT_NAME = Strings.EXPECT_NAME;
    /**
     * The {@value #EXPECT_NAME} header name.
     * Indicates that particular server behaviors are required by the client.
     */
    public static final HeaderName EXPECT = HeaderNameEnum.EXPECT;
    /**
     * The {@value} header name.
     * Disclose original information of a client connecting to a web server through an HTTP proxy.
     */
    public static final String FORWARDED_NAME = Strings.FORWARDED_NAME;
    /**
     * The {@value #FORWARDED_NAME} header name.
     * Disclose original information of a client connecting to a web server through an HTTP proxy.
     */
    public static final HeaderName FORWARDED = HeaderNameEnum.FORWARDED;
    /**
     * The {@value} header name.
     * The email address of the user making the request.
     */
    public static final String FROM_NAME = Strings.FROM_NAME;
    /**
     * The {@value #FROM_NAME} header name.
     * The email address of the user making the request.
     */
    public static final HeaderName FROM = HeaderNameEnum.FROM;
    /**
     * The {@value} header name.
     * The domain name of the server (for virtual hosting), and the TCP port number on which the server is listening.
     * The port number may be omitted if the port is the standard port for the service requested.
     */
    public static final String HOST_NAME = Strings.HOST_NAME;
    /**
     * The {@value #HOST_NAME} header name.
     * The domain name of the server (for virtual hosting), and the TCP port number on which the server is listening.
     * The port number may be omitted if the port is the standard port for the service requested.
     */
    public static final HeaderName HOST = HeaderNameEnum.HOST;
    /**
     * The {@value} header name.
     * Only perform the action if the client supplied entity matches the same entity on the server. This is mainly
     * for methods like PUT to only update a resource if it has not been modified since the user last updated it.
     */
    public static final String IF_MATCH_NAME = Strings.IF_MATCH_NAME;
    /**
     * The {@value #IF_MATCH_NAME} header name.
     * Only perform the action if the client supplied entity matches the same entity on the server. This is mainly
     * for methods like PUT to only update a resource if it has not been modified since the user last updated it.
     */
    public static final HeaderName IF_MATCH = HeaderNameEnum.IF_MATCH;
    /**
     * The {@value} header name.
     * Allows a 304 Not Modified to be returned if content is unchanged.
     */
    public static final String IF_MODIFIED_SINCE_NAME = Strings.IF_MODIFIED_SINCE_NAME;
    /**
     * The {@value #IF_MODIFIED_SINCE_NAME} header name.
     * Allows a 304 Not Modified to be returned if content is unchanged.
     */
    public static final HeaderName IF_MODIFIED_SINCE = HeaderNameEnum.IF_MODIFIED_SINCE;
    /**
     * The {@value} header name.
     * Allows a 304 Not Modified to be returned if content is unchanged, based on {@link #ETAG}.
     */
    public static final String IF_NONE_MATCH_NAME = Strings.IF_NONE_MATCH_NAME;
    /**
     * The {@value #IF_NONE_MATCH_NAME} header name.
     * Allows a 304 Not Modified to be returned if content is unchanged, based on {@link #ETAG}.
     */
    public static final HeaderName IF_NONE_MATCH = HeaderNameEnum.IF_NONE_MATCH;
    /**
     * The {@value} header name.
     * If the entity is unchanged, send me the part(s) that I am missing; otherwise, send me the entire new entity.
     */
    public static final String IF_RANGE_NAME = Strings.IF_RANGE_NAME;
    /**
     * The {@value #IF_RANGE_NAME} header name.
     * If the entity is unchanged, send me the part(s) that I am missing; otherwise, send me the entire new entity.
     */
    public static final HeaderName IF_RANGE = HeaderNameEnum.IF_RANGE;
    /**
     * The {@value} header name.
     * Only send The {@code response if The Entity} has not been modified since a specific time.
     */
    public static final String IF_UNMODIFIED_SINCE_NAME = Strings.IF_UNMODIFIED_SINCE_NAME;
    /**
     * The {@value #IF_UNMODIFIED_SINCE_NAME} header name.
     * Only send The {@code response if The Entity} has not been modified since a specific time.
     */
    public static final HeaderName IF_UNMODIFIED_SINCE = HeaderNameEnum.IF_UNMODIFIED_SINCE;
    /**
     * The {@value} header name.
     * Limit the number of times the message can be forwarded through proxies or gateways.
     */
    public static final String MAX_FORWARDS_NAME = Strings.MAX_FORWARDS_NAME;
    /**
     * The {@value #MAX_FORWARDS_NAME} header name.
     * Limit the number of times the message can be forwarded through proxies or gateways.
     */
    public static final HeaderName MAX_FORWARDS = HeaderNameEnum.MAX_FORWARDS;
    /**
     * The {@value} header name.
     * Initiates a request for cross-origin resource sharing (asks server for an
     * {@value #ACCESS_CONTROL_ALLOW_ORIGIN_NAME} response field).
     */
    public static final String ORIGIN_NAME = Strings.ORIGIN_NAME;
    /**
     * The {@value #ORIGIN_NAME} header name.
     * Initiates a request for cross-origin resource sharing (asks server for an
     * {@value #ACCESS_CONTROL_ALLOW_ORIGIN_NAME}
     * response field).
     */
    public static final HeaderName ORIGIN = HeaderNameEnum.ORIGIN;
    /**
     * The {@value} header name.
     * Proxy authentication information.
     */
    public static final String PROXY_AUTHENTICATE_NAME = Strings.PROXY_AUTHENTICATE_NAME;
    /**
     * The {@value #PROXY_AUTHENTICATE_NAME} header name.
     * Proxy authentication information.
     */
    public static final HeaderName PROXY_AUTHENTICATE = HeaderNameEnum.PROXY_AUTHENTICATE;
    /**
     * The {@value} header name.
     * Proxy authorization information.
     */
    public static final String PROXY_AUTHORIZATION_NAME = Strings.PROXY_AUTHORIZATION_NAME;
    /**
     * The {@value #PROXY_AUTHORIZATION_NAME} header name.
     * Proxy authorization information.
     */
    public static final HeaderName PROXY_AUTHORIZATION = HeaderNameEnum.PROXY_AUTHORIZATION;
    /**
     * The {@value} header name.
     * Request only part of an entity. Bytes are numbered from 0.
     */
    public static final String RANGE_NAME = Strings.RANGE_NAME;
    /**
     * The {@value #RANGE_NAME} header name.
     * Request only part of an entity. Bytes are numbered from 0.
     */
    public static final HeaderName RANGE = HeaderNameEnum.RANGE;
    /**
     * The {@value} header name.
     * This is the address of the previous web page from which a link to the currently requested page was followed.
     * (The {@code word <i>referrer</i>} has been misspelled in The
     * {@code RFC as well as in most implementations to the point that it} has
     * become standard usage and is considered correct terminology.)
     */
    public static final String REFERER_NAME = Strings.REFERER_NAME;
    /**
     * The {@value #REFERER_NAME} header name.
     * This is the address of the previous web page from which a link to the currently requested page was followed.
     * (The {@code word <i>referrer</i>} has been misspelled in The
     * {@code RFC as well as in most implementations to the point that it} has
     * become standard usage and is considered correct terminology.)
     */
    public static final HeaderName REFERER = HeaderNameEnum.REFERER;
    /**
     * The {@value} header name.
     */
    public static final String REFRESH_NAME = Strings.REFRESH_NAME;
    /**
     * The {@value #REFERER_NAME} header name.
     */
    public static final HeaderName REFRESH = HeaderNameEnum.REFRESH;
    /**
     * The {@value} header name.
     * The {@code transfer encodings the user agent is willing to acceptedTypes: the same values as for The Response} header
     * field
     * {@value #TRANSFER_ENCODING_NAME} can be used, plus the <i>trailers</i> value
     * (related to the <i>chunked</i> transfer method)
     * to notify the server it expects to receive additional fields in the trailer after the last, zero-sized, chunk.
     */
    public static final String TE_NAME = Strings.TE_NAME;
    /**
     * The {@value #TE_NAME} header name.
     * The {@code transfer encodings the user agent is willing to acceptedTypes: the same values as for The Response} header
     * field
     * {@value #TRANSFER_ENCODING_NAME} can be used, plus the <i>trailers</i> value
     * (related to the <i>chunked</i> transfer method)
     * to notify the server it expects to receive additional fields in the trailer after the last, zero-sized, chunk.
     */
    public static final HeaderName TE = HeaderNameEnum.TE;
    /**
     * The {@value} header name.
     * The user agent string of the user agent.
     */
    public static final String USER_AGENT_NAME = Strings.USER_AGENT_NAME;
    /**
     * The {@value #USER_AGENT_NAME} header name.
     * The user agent string of the user agent.
     */
    public static final HeaderName USER_AGENT = HeaderNameEnum.USER_AGENT;
    /**
     * The {@value} header name.
     * Informs the server of proxies through which the request was sent.
     */
    public static final String VIA_NAME = Strings.VIA_NAME;
    /**
     * The {@value #VIA_NAME} header name.
     * Informs the server of proxies through which the request was sent.
     */
    public static final HeaderName VIA = HeaderNameEnum.VIA;
    /**
     * The {@value} header name.
     * Specifies which patch document formats this server supports.
     */
    public static final String ACCEPT_PATCH_NAME = Strings.ACCEPT_PATCH_NAME;
    /**
     * The {@value #ACCEPT_PATCH_NAME} header name.
     * Specifies which patch document formats this server supports.
     */
    public static final HeaderName ACCEPT_PATCH = HeaderNameEnum.ACCEPT_PATCH;
    /**
     * The {@value} header name.
     * What partial content range types this server supports via byte serving.
     */
    public static final String ACCEPT_RANGES_NAME = Strings.ACCEPT_RANGES_NAME;
    /**
     * The {@value #ACCEPT_RANGES_NAME} header name.
     * What partial content range types this server supports via byte serving.
     */
    public static final HeaderName ACCEPT_RANGES = HeaderNameEnum.ACCEPT_RANGES;
    /**
     * The {@value} header name.
     * The {@code age The Object} has been in a proxy cache in seconds.
     */
    public static final String AGE_NAME = Strings.AGE_NAME;
    /**
     * The {@value #AGE_NAME} header name.
     * The {@code age The Object} has been in a proxy cache in seconds.
     */
    public static final HeaderName AGE = HeaderNameEnum.AGE;
    /**
     * The {@value} header name.
     * Valid actions for a specified resource. To be used for a 405 Method not allowed.
     */
    public static final String ALLOW_NAME = Strings.ALLOW_NAME;
    /**
     * The {@value #ALLOW_NAME} header name.
     * Valid actions for a specified resource. To be used for a 405 Method not allowed.
     */
    public static final HeaderName ALLOW = HeaderNameEnum.ALLOW;
    /**
     * The {@value} header name.
     * A server uses <i>Alt-Svc</i> header (meaning Alternative Services) to indicate that its resources can also be
     * accessed at a different network location (host or port) or using a different protocol.
     */
    public static final String ALT_SVC_NAME = Strings.ALT_SVC_NAME;
    /**
     * The {@value #ALT_SVC_NAME} header name.
     * A server uses <i>Alt-Svc</i> header (meaning Alternative Services) to indicate that its resources can also be
     * accessed at a different network location (host or port) or using a different protocol.
     */
    public static final HeaderName ALT_SVC = HeaderNameEnum.ALT_SVC;
    /**
     * The {@value} header name.
     * Tells all caching mechanisms from server to client whether they may cache this object. It is measured in seconds.
     */
    public static final String CACHE_CONTROL_NAME = Strings.CACHE_CONTROL_NAME;
    /**
     * The {@value #CACHE_CONTROL_NAME} header name.
     * Tells all caching mechanisms from server to client whether they may cache this object. It is measured in seconds.
     */
    public static final HeaderName CACHE_CONTROL = HeaderNameEnum.CACHE_CONTROL;
    /**
     * The {@value} header name.
     * Control options for The {@code current connection and list of} hop-by-hop response fields.
     */
    public static final String CONNECTION_NAME = Strings.CONNECTION_NAME;
    /**
     * The {@value #CONNECTION_NAME} header name.
     * Control options for The {@code current connection and list of} hop-by-hop response fields.
     */
    public static final HeaderName CONNECTION = HeaderNameEnum.CONNECTION;
    /**
     * The {@value} header name.
     * An opportunity to raise a <i>File Download</i> dialogue box for a known MIME type with binary format or suggest
     * a filename for dynamic content. Quotes are necessary with special characters.
     */
    public static final String CONTENT_DISPOSITION_NAME = Strings.CONTENT_DISPOSITION_NAME;
    /**
     * The {@value #CONTENT_DISPOSITION_NAME} header name.
     * An opportunity to raise a <i>File Download</i> dialogue box for a known MIME type with binary format or suggest
     * a filename for dynamic content. Quotes are necessary with special characters.
     */
    public static final HeaderName CONTENT_DISPOSITION = HeaderNameEnum.CONTENT_DISPOSITION;
    /**
     * The {@value} header name.
     * The type of encoding used on the data.
     */
    public static final String CONTENT_ENCODING_NAME = Strings.CONTENT_ENCODING_NAME;
    /**
     * The {@value #CONTENT_ENCODING_NAME} header name.
     * The type of encoding used on the data.
     */
    public static final HeaderName CONTENT_ENCODING = HeaderNameEnum.CONTENT_ENCODING;
    /**
     * The {@value} header name.
     * The natural language or languages of the intended audience for the enclosed content.
     */
    public static final String CONTENT_LANGUAGE_NAME = Strings.CONTENT_LANGUAGE_NAME;
    /**
     * The {@value #CONTENT_LANGUAGE_NAME} header name.
     * The natural language or languages of the intended audience for the enclosed content.
     */
    public static final HeaderName CONTENT_LANGUAGE = HeaderNameEnum.CONTENT_LANGUAGE;
    /**
     * The {@value} header name.
     * The length of the response body in octets.
     */
    public static final String CONTENT_LENGTH_NAME = Strings.CONTENT_LENGTH_NAME;
    /**
     * The {@value #CONTENT_LENGTH_NAME} header name.
     * The length of the response body in octets.
     */
    public static final HeaderName CONTENT_LENGTH = HeaderNameEnum.CONTENT_LENGTH;
    /**
     * The {@value} header name.
     * An alternate location for the returned data.
     */
    public static final String CONTENT_LOCATION_NAME = Strings.CONTENT_LOCATION_NAME;
    /**
     * The {@value #CONTENT_LOCATION_NAME} header name.
     * An alternate location for the returned data.
     */
    public static final HeaderName CONTENT_LOCATION = HeaderNameEnum.CONTENT_LOCATION;
    /**
     * The {@value} header name.
     * Where in a full body message this partial message belongs.
     */
    public static final String CONTENT_RANGE_NAME = Strings.CONTENT_RANGE_NAME;
    /**
     * The {@value #CONTENT_RANGE_NAME} header name.
     * Where in a full body message this partial message belongs.
     */
    public static final HeaderName CONTENT_RANGE = HeaderNameEnum.CONTENT_RANGE;
    /**
     * The {@value} header name.
     * The MIME type of this content.
     */
    public static final String CONTENT_TYPE_NAME = Strings.CONTENT_TYPE_NAME;
    /**
     * The {@value #CONTENT_TYPE_NAME} header name.
     * The MIME type of this content.
     */
    public static final HeaderName CONTENT_TYPE = HeaderNameEnum.CONTENT_TYPE;
    /**
     * The {@value} header name.
     * The date and time that the message was sent (in <i>HTTP-date</i> format as defined by RFC 7231).
     */
    public static final String DATE_NAME = Strings.DATE_NAME;
    /**
     * The {@value #DATE_NAME} header name.
     * The date and time that the message was sent (in <i>HTTP-date</i> format as defined by RFC 7231).
     */
    public static final HeaderName DATE = HeaderNameEnum.DATE;
    /**
     * The {@value} header name.
     * An identifier for a specific version of a resource, often a message digest.
     */
    public static final String ETAG_NAME = Strings.ETAG_NAME;
    /**
     * The {@value #ETAG_NAME} header name.
     * An identifier for a specific version of a resource, often a message digest.
     */
    public static final HeaderName ETAG = HeaderNameEnum.ETAG;
    /**
     * The {@value} header name.
     * Gives the date/time after which the response is considered stale (in <i>HTTP-date</i> format as defined by RFC 7231)
     */
    public static final String EXPIRES_NAME = Strings.EXPIRES_NAME;
    /**
     * The {@value #EXPIRES_NAME} header name.
     * Gives the date/time after which the response is considered stale (in <i>HTTP-date</i> format as defined by RFC 7231)
     */
    public static final HeaderName EXPIRES = HeaderNameEnum.EXPIRES;
    /**
     * The {@value} header name.
     * The last modified date for the requested object (in <i>HTTP-date</i> format as defined by RFC 7231)
     */
    public static final String LAST_MODIFIED_NAME = Strings.LAST_MODIFIED_NAME;
    /**
     * The {@value #LAST_MODIFIED_NAME} header name.
     * The last modified date for the requested object (in <i>HTTP-date</i> format as defined by RFC 7231)
     */
    public static final HeaderName LAST_MODIFIED = HeaderNameEnum.LAST_MODIFIED;
    /**
     * The {@value} header name.
     * Used to express a typed relationship with another resource, where the relation type is defined by RFC 5988.
     */
    public static final String LINK_NAME = Strings.LINK_NAME;
    /**
     * The {@value #LINK_NAME} header name.
     * Used to express a typed relationship with another resource, where the relation type is defined by RFC 5988.
     */
    public static final HeaderName LINK = HeaderNameEnum.LINK;
    /**
     * The {@value} header name.
     * Used in redirection, or whenRequest a new resource has been created.
     */
    public static final String LOCATION_NAME = Strings.LOCATION_NAME;
    /**
     * The {@value #LOCATION_NAME} header name.
     * Used in redirection, or whenRequest a new resource has been created.
     */
    public static final HeaderName LOCATION = HeaderNameEnum.LOCATION;
    /**
     * The {@value} header name.
     * Implementation-specific fields that may have various effects anywhere along the request-response chain.
     */
    public static final String PRAGMA_NAME = Strings.PRAGMA_NAME;
    /**
     * The {@value #PRAGMA_NAME} header name.
     * Implementation-specific fields that may have various effects anywhere along the request-response chain.
     */
    public static final HeaderName PRAGMA = HeaderNameEnum.PRAGMA;
    /**
     * The {@value} header name.
     * HTTP Public Key Pinning, announces hash of website's authentic TLS certificate.
     */
    public static final String PUBLIC_KEY_PINS_NAME = Strings.PUBLIC_KEY_PINS_NAME;
    /**
     * The {@value #PUBLIC_KEY_PINS_NAME} header name.
     * HTTP Public Key Pinning, announces hash of website's authentic TLS certificate.
     */
    public static final HeaderName PUBLIC_KEY_PINS = HeaderNameEnum.PUBLIC_KEY_PINS;
    /**
     * The {@value} header name.
     * If an entity is temporarily unavailable, this instructs the client to try again later. Value could be a specified
     * period of time (in seconds) or an HTTP-date.
     */
    public static final String RETRY_AFTER_NAME = Strings.RETRY_AFTER_NAME;
    /**
     * The {@value #RETRY_AFTER_NAME} header name.
     * If an entity is temporarily unavailable, this instructs the client to try again later. Value could be a specified
     * period of time (in seconds) or an HTTP-date.
     */
    public static final HeaderName RETRY_AFTER = HeaderNameEnum.RETRY_AFTER;
    /**
     * The {@value} header name.
     * A name for the server.
     */
    public static final String SERVER_NAME = Strings.SERVER_NAME;
    /**
     * The {@value #SERVER_NAME} header name.
     * A name for the server.
     */
    public static final HeaderName SERVER = HeaderNameEnum.SERVER;
    /**
     * The {@value} header name.
     * An HTTP cookie set directive.
     */
    public static final String SET_COOKIE_NAME = Strings.SET_COOKIE_NAME;
    /**
     * The {@value #SET_COOKIE_NAME} header name.
     * An HTTP cookie set directive.
     */
    public static final HeaderName SET_COOKIE = HeaderNameEnum.SET_COOKIE;
    /**
     * The {@value} header name.
     * An HTTP cookie set directive.
     */
    public static final String SET_COOKIE2_NAME = Strings.SET_COOKIE2_NAME;
    /**
     * The {@value #SET_COOKIE2_NAME} header name.
     * An HTTP cookie set directive.
     */
    public static final HeaderName SET_COOKIE2 = HeaderNameEnum.SET_COOKIE2;
    /**
     * The {@value} header name.
     * A HSTS Policy informing The {@code HTTP client} how long to cache the HTTPS only policy and whether this applies to
     * subdomains.
     */
    public static final String STRICT_TRANSPORT_SECURITY_NAME = Strings.STRICT_TRANSPORT_SECURITY_NAME;
    /**
     * The {@value #STRICT_TRANSPORT_SECURITY_NAME} header name.
     * A HSTS Policy informing The {@code HTTP client} how long to cache the HTTPS only policy and whether this applies to
     * subdomains.
     */
    public static final HeaderName STRICT_TRANSPORT_SECURITY = HeaderNameEnum.STRICT_TRANSPORT_SECURITY;
    /**
     * The {@value} header name.
     * The Trailer general field value indicates that the given set of} header fields is present in the trailer of
     * a message encoded with chunked transfer coding.
     */
    public static final String TRAILER_NAME = Strings.TRAILER_NAME;
    /**
     * The {@value #TRAILER_NAME} header name.
     * The Trailer general field value indicates that the given set of} header fields is present in the trailer of
     * a message encoded with chunked transfer coding.
     */
    public static final HeaderName TRAILER = HeaderNameEnum.TRAILER;
    /**
     * The {@value} header name.
     * The form of encoding used to safely transfer the entity to the user. Currently defined methods are:
     * {@code chunked, compress, deflate, gzip, identity}.
     */
    public static final String TRANSFER_ENCODING_NAME = Strings.TRANSFER_ENCODING_NAME;
    /**
     * The {@value #TRANSFER_ENCODING_NAME} header name.
     * The form of encoding used to safely transfer the entity to the user. Currently defined methods are:
     * {@code chunked, compress, deflate, gzip, identity}.
     */
    public static final HeaderName TRANSFER_ENCODING = HeaderNameEnum.TRANSFER_ENCODING;
    /**
     * The {@value} header name.
     * Tracking Status Value, value suggested to be sent in response to a DNT(do-not-track).
     */
    public static final String TSV_NAME = Strings.TSV_NAME;
    /**
     * The {@value #TSV_NAME} header name.
     * Tracking Status Value, value suggested to be sent in response to a DNT(do-not-track).
     */
    public static final HeaderName TSV = HeaderNameEnum.TSV;
    /**
     * The {@value} header name.
     * Ask to upgrade to another protocol.
     */
    public static final String UPGRADE_NAME = Strings.UPGRADE_NAME;
    /**
     * The {@value #UPGRADE_NAME} header name.
     * Ask to upgrade to another protocol.
     */
    public static final HeaderName UPGRADE = HeaderNameEnum.UPGRADE;
    /**
     * The {@value} header name.
     * Tells downstream proxies how to match future request headers to decide whether the cached response can be used rather
     * than requesting a fresh one from the origin server.
     */
    public static final String VARY_NAME = Strings.VARY_NAME;
    /**
     * The {@value #VARY_NAME} header name.
     * Tells downstream proxies how to match future request headers to decide whether the cached response can be used rather
     * than requesting a fresh one from the origin server.
     */
    public static final HeaderName VARY = HeaderNameEnum.VARY;
    /**
     * The {@value} header name.
     * A general warning about possible problems with the entity body.
     */
    public static final String WARNING_NAME = Strings.WARNING_NAME;
    /**
     * The {@value #WARNING_NAME} header name.
     * A general warning about possible problems with the entity body.
     */
    public static final HeaderName WARNING = HeaderNameEnum.WARNING;
    /**
     * The {@value} header name.
     * Indicates the authentication scheme that should be used to access the requested entity.
     */
    public static final String WWW_AUTHENTICATE_NAME = Strings.WWW_AUTHENTICATE_NAME;
    /**
     * The {@value #WWW_AUTHENTICATE_NAME} header name.
     * Indicates the authentication scheme that should be used to access the requested entity.
     */
    public static final HeaderName WWW_AUTHENTICATE = HeaderNameEnum.WWW_AUTHENTICATE;
    /**
     * The {@value} header name.
     * Corresponds to the certificate CN subject value when client authentication enabled.
     * This header will be removed if it is part of the request.
     */
    public static final String X_HELIDON_CN_NAME = Strings.X_HELIDON_CN_NAME;
    /**
     * The {@value #X_HELIDON_CN_NAME} header name.
     * Corresponds to the certificate CN subject value when client authentication enabled.
     * This header will be removed if it is part of the request.
     */
    public static final HeaderName X_HELIDON_CN = HeaderNameEnum.X_HELIDON_CN;
    /**
     * The {@value} header name.
     * Represents the originating client and intervening proxies when the request has passed through one or more proxies.
     */
    public static final String X_FORWARDED_FOR_NAME = Strings.X_FORWARDED_FOR_NAME;
    /**
     * The {@value #X_FORWARDED_FOR_NAME} header name.
     * Represents the originating client and intervening proxies when the request has passed through one or more proxies.
     */
    public static final HeaderName X_FORWARDED_FOR = HeaderNameEnum.X_FORWARDED_FOR;
    /**
     * The {@value} header name.
     * Represents the host specified by the originating client when the request has passed through one or more proxies.
     */
    public static final String X_FORWARDED_HOST_NAME = Strings.X_FORWARDED_HOST_NAME;
    /**
     * The {@value #X_FORWARDED_HOST_NAME} header name.
     * Represents the host specified by the originating client when the request has passed through one or more proxies.
     */
    public static final HeaderName X_FORWARDED_HOST = HeaderNameEnum.X_FORWARDED_HOST;

    /**
     * The {@value} header name.
     * Represents the port specified by the originating client when the request has passed through one or more proxies.
     */
    public static final String X_FORWARDED_PORT_NAME = Strings.X_FORWARDED_PORT_NAME;
    /**
     * The {@value #X_FORWARDED_PORT_NAME} header name.
     * Represents the port specified by the originating client when the request has passed through one or more proxies.
     */
    public static final HeaderName X_FORWARDED_PORT = HeaderNameEnum.X_FORWARDED_PORT;

    /**
     * The {@value} header name.
     * Represents the path prefix to be applied to relative paths resolved against this request when the request has passed
     * through one or more proxies.
     */
    public static final String X_FORWARDED_PREFIX_NAME = Strings.X_FORWARDED_PREFIX_NAME;
    /**
     * The {@value #X_FORWARDED_PREFIX_NAME} header name.
     * Represents the path prefix to be applied to relative paths resolved against this request when the request has passed
     * through one or more proxies.
     */
    public static final HeaderName X_FORWARDED_PREFIX = HeaderNameEnum.X_FORWARDED_PREFIX;
    /**
     * The {@value} header name.
     * Represents the protocol specified by the originating client when the request has passed through one or more proxies.
     */
    public static final String X_FORWARDED_PROTO_NAME = Strings.X_FORWARDED_PROTO_NAME;
    /**
     * The {@value #X_FORWARDED_PROTO_NAME} header name.
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
