/*
 * Copyright (c) 2017, 2022 Oracle and/or its affiliates.
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

package io.helidon.reactive.webserver;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpException;
import io.helidon.common.reactive.Single;
import io.helidon.common.uri.UriInfo;
import io.helidon.common.uri.UriQuery;
import io.helidon.reactive.media.common.MessageBodyReadableContent;
import io.helidon.tracing.SpanContext;
import io.helidon.tracing.Tracer;

/**
 * Represents HTTP Request and provides WebServer related API.
 */
public interface ServerRequest {

    /**
     * Continue request processing on the next registered handler.
     * <p>
     * If error is being handled, this is identical to calling {@link #next(Throwable)}
     * with a cause of the error.
     */
    void next();

    /**
     * Continues or enters an error branch of a request processing.
     * This call has identical effect to throwing the exception {@code t} except that
     * the exception is directly passed to a next registered {@link ErrorHandler}
     * and this it's faster.
     * <p>
     * It is not possible to leave error request processing and continue in registered {@link Handler}.
     *
     * @param t a cause that is directly passed to a next registered {@link ErrorHandler}
     */
    void next(Throwable t);

    /**
     * Returns actual {@link WebServer} instance.
     *
     * @return actual {@code WebServer} instance
     */
    WebServer webServer();

    /**
     * Returns a request context as a child of {@link WebServer} context.
     *
     * @return a request context
     */
    Context context();

    /**
     * Returns the Internet Protocol (IP) address of the interface on which the request was received.
     *
     * @return an address
     */
    String localAddress();

    /**
     * Returns the Internet Protocol (IP) port number of the interface on which the request was received.
     *
     * @return the port number
     */
    int localPort();

    /**
     * Returns the Internet Protocol (IP) address of the client or last proxy that sent the request.
     *
     * @return the address of the client that sent the request
     */
    String remoteAddress();

    /**
     * Returns the Internet Protocol (IP) source port of the client or last proxy that sent the request.
     *
     * @return the port number.
     */
    int remotePort();

    /**
     * Returns an indicating whether this request was made using a secure channel, such as HTTPS.
     *
     * @return {@code true} if the request was made using a secure channel
     */
    boolean isSecure();

    /**
     * Returns http request headers.
     *
     * @return an http headers
     */
    RequestHeaders headers();

    /**
     * Returns {@link MessageBodyReadableContent} reactive representation of the request content.
     *
     * @return a request content
     * @see MessageBodyReadableContent
     */
    MessageBodyReadableContent content();

    /**
     * A unique correlation ID that is associated with this request and its associated response.
     *
     * @return a unique correlation ID associated with this request and its response
     */
    long requestId();

    /**
     * Returns a span context related to the current request.
     * <p>
     * {@code SpanContext} is a tracing component from <a href="http://opentracing.io">opentracing.io</a> standard.
     *
     * @return the related span context, empty if not enabled
     */
    Optional<SpanContext> spanContext();

    /**
     * Returns the {@link io.helidon.tracing.Tracer} associated with {@link WebServer}.
     *
     * @return the tracer associated, or {@link io.helidon.tracing.Tracer#global()}
     */
    Tracer tracer();

    /**
     * Request to close the connection and report success or failure asynchronously with returned single.
     * After connection is closed it is not possible to use it again.
     *
     * @return Single completed when connection is closed.
     */
    Single<Void> closeConnection();

    /**
     * Absolute URI of the incoming request, including query parameters and fragment.
     * The host and port are obtained from the interface this server listens on ({@code host} header is not used).
     *
     * @return the URI of incoming request
     */
    default URI absoluteUri() {
        try {
            // Use raw string representation and URL to avoid re-encoding chars like '%'
            URI partialUri = new URL(isSecure() ? "https" : "http", localAddress(),
                                     localPort(), path().absolute().toRawString()).toURI();
            StringBuilder sb = new StringBuilder(partialUri.toString());
            if (uri().toString().endsWith("/") && sb.charAt(sb.length() - 1) != '/') {
                sb.append('/');
            }

            // unfortunately, the URI constructor encodes the 'query' and 'fragment' which is totally silly
            if (query() != null && !query().isEmpty()) {
                sb.append("?")
                        .append(query());
            }
            if (fragment() != null && !fragment().isEmpty()) {
                sb.append("#")
                        .append(fragment());
            }
            return new URI(sb.toString());
        } catch (URISyntaxException | MalformedURLException e) {
            throw new HttpException("Unable to parse request URL", Http.Status.BAD_REQUEST_400, e);
        }
    }

    /**
     * Returns an HTTP request method. See also {@link Http.Method HTTP standard methods} utility class.
     *
     * @return an HTTP method
     * @see Http.Method
     */
    Http.Method method();

    /**
     * Returns an HTTP version from the request line.
     * <p>
     * See {@link Http.Version HTTP Version} enumeration for supported versions.
     * <p>
     * If communication starts as a {@code HTTP/1.1} with {@code h2c} upgrade, then it will be automatically
     * upgraded and this method returns {@code HTTP/2.0}.
     *
     * @return an HTTP version
     */
    Http.Version version();

    /**
     * Returns a Request-URI (or alternatively path) as defined in request line.
     *
     * @return a request URI
     */
    URI uri();

    /**
     * Returns an encoded query string without leading '?' character.
     *
     * @return an encoded query string
     */
    String query();

    /**
     * Returns query parameters.
     *
     * @return an parameters representing query parameters
     */
    UriQuery queryParams();

    /**
     * Returns a path which was accepted by matcher in actual routing. It is path without a context root
     * of the routing.
     * <p>
     * Use {@link Path#absolute()} method to obtain absolute request URI path representation.
     * <p>
     * Returned {@link Path} also provide access to path template parameters. An absolute path then provides access to
     * all (including) context parameters if any. In case of conflict between parameter names, most recent value is returned.
     *
     * @return a path
     */
    Path path();

    /**
     * Returns a decoded request URI fragment without leading hash '#' character.
     *
     * @return a decoded URI fragment
     */
    String fragment();

    /**
     * URI as requested by the originating client (to the best of our ability to compute it).
     * By default, the URI is from the {@link Http.Header#HOST} header on the current request.
     * If requested URI discovery is enabled by configuration, additional headers (such as {@link Http.Header#FORWARDED})
     * may be used to derive the originally-requested URI.
     *
     * @return uri info that can be used for redirects
     */
    UriInfo requestedUri();

    /**
     * Represents requested normalised URI path.
     */
    interface Path {

        /**
         * Returns value of single parameter resolved from path pattern.
         *
         * @param name a parameter name
         * @return a parameter value or {@code null} if not exist
         */
        String param(String name);

        /**
         * Returns path as a list of its segments.
         *
         * @return a list of path segments
         */
        List<String> segments();

        /**
         * Returns a path string representation with leading slash.
         *
         * @return a path
         */
        String toString();

        /**
         * Returns a path string representation with leading slash without
         * any character decoding.
         *
         * @return an undecoded path
         */
        String toRawString();

        /**
         * If the instance represents a path relative to some context root then returns absolute requested path otherwise
         * returns this instance.
         * <p>
         * The absolute path also contains access to path parameters defined in context matchers. If there is
         * name conflict then value represents latest matcher result.
         *
         * @return an absolute requested URI path
         */
        Path absolute();
    }
}
