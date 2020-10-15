/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.http.Http;
import io.helidon.common.http.HttpRequest;
import io.helidon.media.common.MessageBodyReadableContent;

import io.opentracing.SpanContext;
import io.opentracing.Tracer;

/**
 * Represents HTTP Request and provides WebServer related API.
 */
public interface ServerRequest extends HttpRequest {

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
     * Returns the {@link io.opentracing.Tracer} associated with {@link io.helidon.webserver.WebServer}.
     *
     * @return the tracer associated, or {@link io.opentracing.util.GlobalTracer#get()}
     */
    Tracer tracer();

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
}
