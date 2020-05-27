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

import io.helidon.common.context.Context;
import io.helidon.common.http.HttpRequest;
import io.helidon.media.common.MessageBodyReadableContent;

import io.opentracing.Span;
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
     * Returns a span connected with current {@link Handler} call.
     * <p>
     * {@code Span} is a tracing component from <a href="http://opentracing.io">opentracing.io</a> standard.
     *
     * @return a current span
     * @deprecated use {@link #spanContext()} instead
     */
    @Deprecated
    Span span();

    /**
     * Returns a span context related to the current request.
     * <p>
     * {@code SpanContext} is a tracing component from <a href="http://opentracing.io">opentracing.io</a> standard.
     *
     * @return the related span context, may be null if not enabled
     * @deprecated this method will have a different return type in next backward incompatible version
     *  of Helidon. It will return {@code Optional<SpanContext>}. All methods that can return null will use
     *  {@link java.util.Optional}
     */
    SpanContext spanContext();

    /**
     * Returns the {@link io.opentracing.Tracer} associated with {@link io.helidon.webserver.WebServer}.
     *
     * @return the tracer associated, or {@link io.opentracing.util.GlobalTracer#get()}
     */
    Tracer tracer();
}
