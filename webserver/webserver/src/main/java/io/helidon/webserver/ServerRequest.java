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
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import io.helidon.common.reactive.Flow;

import io.opentracing.Span;
import io.opentracing.SpanContext;

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
    ContextualRegistry context();

    /**
     * Returns an HTTP request method. See also {@link Http.Method HTTP standard methods} utility class.
     *
     * @return an HTTP method
     * @see Http.Method
     */
    Http.RequestMethod method();

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
    Parameters queryParams();

    /**
     * Returns a path which was accepted by {@link PathMatcher} in actual routing. It is path without a context root
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
     * Returns {@link Content reactive representation} of the request content.
     *
     * @return a request content
     * @see Content
     */
    Content content();

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
     * @return the related span context
     */
    SpanContext spanContext();

    /**
     * Represents an HTTP request content as a {@link Flow.Publisher publisher} of {@link RequestChunk RequestChunks} with specific
     * features.
     * <h3>Default publisher contract</h3>
     * Default publisher accepts only single subscriber. Other subscribers receives
     * {@link Flow.Subscriber#onError(Throwable) onError()}.
     * <p>
     * {@link RequestChunk} provided by {@link Flow.Subscriber#onNext(Object) onNext()} method <b>must</b> be consumed in this
     * method call. Buffer can be reused reused by network infrastructure as soon as {@code onNext()} method returns.
     * While this behavior can be inconvenient but it helps to provide excellent performance.
     *
     * <h3>Publisher Overwrite.</h3>
     * It is possible to modify contract of the original publisher by registration of the a publisher using
     * {@link #registerFilter(Function)} method. It can be used to wrap or replace previously registered (or default) publisher.
     *
     * <h3>Entity Readers</h3>
     * It is possible to register function to convert publisher to {@link CompletionStage} of a single entity using
     * {@link #registerReader(Class, Reader)} or {@link #registerReader(Predicate, Reader)} methods. It is then possible
     * to use {@link #as(Class)} method to obtain such entity.
     */
    interface Content extends Flow.Publisher<RequestChunk> {

        /**
         * If possible, adds the given Subscriber to this publisher. This publisher is effectively
         * either the original publisher
         * or the last publisher registered by the method {@link #registerFilter(Function)}.
         * <p>
         * Note that the original publisher allows only a single subscriber and requires the passed
         * {@link RequestChunk} in the {@link io.helidon.common.reactive.Flow.Subscriber#onNext(Object)} call
         * to be consumed before the method completes as specified by the {@link Content Default Publisher Contract}.
         *
         * @param subscriber the subscriber
         * @throws NullPointerException if subscriber is null
         */
        @Override
        void subscribe(Flow.Subscriber<? super RequestChunk> subscriber);

        /**
         * Registers a filter that allows a control of the original publisher.
         * <p>
         * The provided function is evaluated upon calling either of {@link #subscribe(Flow.Subscriber)}
         * or {@link #as(Class)}.
         * The first evaluation of the function transforms the original publisher to a new publisher.
         * Any subsequent evaluation receives the publisher transformed by the last previously
         * registered filter.
         * It is up to the implementor of the given function to respect the contract of both the original
         * publisher and the previously registered ones.
         *
         * @param function a function that transforms a given publisher (that is either the original
         *                 publisher or the publisher transformed by the last previously registered filter).
         */
        void registerFilter(Function<Flow.Publisher<RequestChunk>, Flow.Publisher<RequestChunk>> function);

        /**
         * Registers a reader for a later use with an appropriate {@link #as(Class)} method call.
         * <p>
         * The reader must transform the published byte buffers into a completion stage of the
         * requested type.
         * <p>
         * Upon calling {@link #as(Class)} a matching reader is searched in the same order as the
         * readers were registered. If no matching reader is found, or when the function throws
         * an exception, the resulting completion stage ends exceptionally.
         *
         * @param type   the requested type the completion stage is be associated with.
         * @param reader the reader as a function that transforms a publisher into completion stage.
         *               If an exception is thrown, the resulting completion stage of
         *               {@link #as(Class)} method call ends exceptionally.
         * @param <T>    the requested type
         */
        <T> void registerReader(Class<T> type, Reader<T> reader);

        /**
         * Registers a reader for a later use with an appropriate {@link #as(Class)} method call.
         * <p>
         * The reader must transform the published byte buffers into a completion stage of the
         * requested type.
         * <p>
         * Upon calling {@link #as(Class)} a matching reader is searched in the same order as the
         * readers were registered. If no matching reader is found or when the predicate throws
         * an exception, or when the function throws an exception, the resulting completion stage
         * ends exceptionally.
         *
         * @param predicate the predicate that determines whether the registered reader can handle
         *                  the requested type. If an exception is thrown, the resulting completion
         *                  stage of {@link #as(Class)} method call ends exceptionally.
         * @param reader    the reader as a function that transforms a publisher into completion stage.
         *                  If an exception is thrown, the resulting completion stage of
         *                  {@link #as(Class)} method call ends exceptionally.
         * @param <T>       the requested type
         */
        <T> void registerReader(Predicate<Class<?>> predicate, Reader<T> reader);

        /**
         * Consumes and converts the request content into a completion stage of the requested type.
         * <p>
         * The conversion requires an appropriate reader to be already registered
         * (see {@link #registerReader(Predicate, Reader)}). If no such reader is found, the
         * resulting completion stage ends exceptionally.
         *
         * @param type the requested type class
         * @param <T>  the requested type
         * @return a completion stage of the requested type
         */
        <T> CompletionStage<? extends T> as(Class<T> type);
    }

    /**
     * The Reader transforms a byte buffer publisher into a completion stage of the associated type.
     *
     * @param <R> the requested type
     */
    interface Reader<R> extends BiFunction<Flow.Publisher<RequestChunk>, Class<? super R>, CompletionStage<? extends R>> {

        /**
         * Transforms a publisher into a completion stage.
         * If an exception is thrown, the resulting completion stage of
         * {@link Content#as(Class)} method call ends exceptionally.
         *
         * @param publisher the publisher to transform
         * @param clazz     the requested type to be returned as a completion stage. The purpose of
         *                  this parameter is to know what the user of this Reader actually requested.
         * @return the result as a completion stage
         */
        @Override
        CompletionStage<? extends R> apply(Flow.Publisher<RequestChunk> publisher, Class<? super R> clazz);

        /**
         * Transforms a publisher into a completion stage.
         * If an exception is thrown, the resulting completion stage of
         * {@link Content#as(Class)} method call ends exceptionally.
         * <p>
         * The default implementation calls {@link #apply(Flow.Publisher, Class)} with {@link Object} as
         * the class parameter.
         *
         * @param publisher the publisher to transform
         * @return the result as a completion stage
         */
        default CompletionStage<? extends R> apply(Flow.Publisher<RequestChunk> publisher) {
            return apply(publisher, Object.class);
        }

        /**
         * Transforms a publisher into a completion stage.
         * If an exception is thrown, the resulting completion stage of
         * {@link Content#as(Class)} method call ends exceptionally.
         * <p>
         * The default implementation calls {@link #apply(Flow.Publisher, Class)} with {@link Object} as
         * the class parameter.
         *
         * @param publisher the publisher to transform
         * @param type      the desired type to cast the guarantied {@code R} type to
         * @param <T>       the desired type to cast the guarantied {@code R} type to
         * @return the result as a completion stage which might end exceptionally with
         * {@link ClassCastException} if the {@code R} type wasn't possible to cast
         * to {@code T}
         */
        default <T extends R> CompletionStage<? extends T> applyAndCast(Flow.Publisher<RequestChunk> publisher, Class<T> type) {
            // if this was implemented as (CompletionStage<? extends T>) apply(publisher, (Class<R>) clazz);
            // the class cast exception might occur outside of the completion stage which might be confusing
            return apply(publisher, (Class<R>) type).thenApply(type::cast);
        }
    }

    /**
     * Represents requested normalised URI path processed by {@link PathMatcher}.
     */
    interface Path {

        /**
         * Returns value of single parameter resolved by relevant {@link PathMatcher}.
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
         * If the instance represents a path relative to some context root then returns absolute requested path otherwise
         * returns this instance.
         * <p>
         * The absolute path also contains access to path parameters defined in context {@link PathMatcher}s. If there is
         * name conflict then value represents latest matcher result.
         *
         * @return an absolute requested URI path
         */
        Path absolute();
    }
}
