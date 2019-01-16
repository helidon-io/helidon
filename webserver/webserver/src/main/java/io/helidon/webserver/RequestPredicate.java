/*
 * Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.http.Http.Method;
import io.helidon.common.http.MediaType;

/**
 * Fluent API to compose complex request conditions.
 *
 * First you start by creating a new instance with the {@link #create()} method,
 * then you chain methods that represent a logical expression, finally you terminate
 * with s using the {@link #thenApply(Handler) } method.
 *
 * <h3>Examples</h3>
 * <p>Invoke a {@link Handler} only when the request contains a header {@code foo}
 * and accepts {@code text/plain}, otherwise return a response with {@code 404} code.
 * <pre>{@code
 * RequestPredicate.create()
 *                 .containsHeader("foo")
 *                 .accepts(MediaType.TEXT_PLAIN)
 *                 .thenApply((req, resp) -> {
 *                     // handler logic
 *                 });
 * }</pre>
 * <p>Invoke a {@link Handler} that is invoked only when the request contains
 * a header {@code foo} header, otherwise invoke another handler that throws an
 * exception.
 * <pre>{@code
 * RequestPredicate.create()
 *                 .containsHeader("foo")
 *                 .thenApply((req, resp) -> {
 *                     // handler logic
 *                 })
 *                 .otherwise(req, resp) -> {
 *                     throw new RuntimeException("Missing 'foo' header!");
 *                 });
 * }</pre>
 */
public final class RequestPredicate {

    /**
     * An expression that returns the current value.
     */
    private static final Expression EMPTY_EXPR = (a, b) -> a;

    /**
     * The first predicate in the expression chain.
     */
    private final RequestPredicate tail;

    /**
     * The next predicate in the expression chain.
     */
    private RequestPredicate next;

    /**
     * The expression for this predicate.
     */
    private final Expression expr;

    /**
     * Create the new empty predicate.
     */
    private RequestPredicate(){
        this.tail = this;
        this.next = null;
        this.expr = EMPTY_EXPR;
    }

    /**
     * Create a composed predicate with the given expression.
     * @param tail the first predicate in the chain
     * @param expr the expression for the new predicate
     */
    private RequestPredicate(final RequestPredicate tail,
            final Expression expr){

        this.tail = tail;
        this.next = null;
        this.expr = expr;
    }

    /**
     * Create a composed predicate and add it in the predicate chain.
     * @param newExpr the expression for the new predicate
     * @return the created predicate
     */
    private RequestPredicate nextExpr(final Expression newExpr){
        this.next = new RequestPredicate(this.tail, newExpr);
        return this.next;
    }

    /**
     * Set the {@link Handler} to use when this predicate matches the request.
     *
     * @param handler handler to use this predicate instance matches
     * @return instance of {@link ConditionalHandler} that can be used to
     * specify another {@link Handler} to use when this predicates does not
     * match the request
     * @see ConditionalHandler#otherwise(Handler)
     */
    public ConditionalHandler thenApply(final Handler handler) {
        return new ConditionalHandler(this, handler);
    }

    /**
     * Compute the current value for this predicate.
     * @param request the server request
     * @return the computed value
     */
    public boolean test(final ServerRequest request) {
        return eval(/* initial value */ true, this.tail, request);
    }

    /**
     * Returns a composed predicate that represents a logical AND expression
     * between this predicate and another predicate.
     *
     * @param predicate predicate to compose with
     * @return composed predicate between the logical expression of the current
     * predicate <b>and</b> the provided predicate
     */
    public RequestPredicate and(final Predicate<ServerRequest> predicate) {
        return nextExpr((exprVal, req) -> exprVal && predicate.test(req));
    }

    /**
     * Returns a composed predicate that represents a logical OR expression
     * between this predicate and another predicate.
     *
     * @param predicate predicate that compute the new value
     * @return composed predicate between the logical expression of the current
     * predicate <b>or</b> the provided predicate
     */
    public RequestPredicate or(final Predicate<ServerRequest> predicate) {
        return nextExpr((exprVal, req) -> exprVal || predicate.test(req));
    }

    /**
     * Return a predicate that represents the logical negation of this predicate.
     * @return new predicate that represents the logical negation of this predicate.
     */
    public RequestPredicate negate() {
        return nextExpr((exprVal, req) -> !exprVal);
    }

    /**
     * Accepts only requests with one of specified HTTP methods.
     *
     * @param methods Acceptable method names
     * @return new predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate isOfMethod(final String... methods) {
        Objects.requireNonNull(methods, "methods");
        return and((req) ->
                Arrays.asList(methods)
                        .stream()
                        .map(String::toUpperCase)
                        .anyMatch(req.method().name()::equals));
    }

    /**
     * Accepts only requests with one of specified HTTP methods.
     *
     * @param methods Acceptable method names
     * @return new predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate isOfMethod(final Method... methods) {
        Objects.requireNonNull(methods, "methods");
        return and((req) ->
                Arrays.asList(methods)
                        .stream()
                        .map(Method::name)
                        .anyMatch(req.method().name()::equals));
    }

    /**
     * Accept requests only when the specified header name exists.
     *
     * @param name header name
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsHeader(final String name) {
        Objects.requireNonNull(name, "header name");
        return and((req) ->
                req.headers().value(name).isPresent());
    }

    /**
     * Accept requests only when the specified header is valid.
     *
     * A header is valid when the supplied predicate matches the header value.
     * If the request contains more than one header, it will be accepted if the
     * predicate matches <b>any</b> of the values.
     *
     * @param name header name
     * @param predicate predicate to match the header value
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or predicate is null
     */
    public RequestPredicate containsHeader(final String name,
            final Predicate<String> predicate) {

        Objects.requireNonNull(name, "header name");
        Objects.requireNonNull(predicate, "header predicate");
        return and((req) -> {
            Optional<String> headerValue = req.headers().value(name);
            return headerValue.isPresent()
                    && predicate.test(headerValue.get());
        });
    }

    /**
     * Accept requests only when the specified header contains a given value.
     *
     * If the request contains more then one header, it will be accepted
     * if <b>any</b> of the values is equal to the provided value.
     *
     * @param name header name
     * @param value the expected header value
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsHeader(final String name,
            final String value) {

        Objects.requireNonNull(name, "header name");
        Objects.requireNonNull(value, "header value");
        return and((req) -> {
            Optional<String> headerValue = req.headers().value(name);
            return headerValue.isPresent()
                    && headerValue.get().equals(value);
        });
    }

    /**
     * Accept requests only when the specified query parameter exists.
     *
     * @param name query parameter name
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsQueryParameter(final String name) {
        Objects.requireNonNull(name, "query param name");
        return and((req) -> req.queryParams()
                .first(name)
                .isPresent());
    }

    /**
     * Accept requests only when the specified query parameter is valid.
     *
     * @param name query parameter name
     * @param predicate to match the query parameter value
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or predicate is null
     */
    public RequestPredicate containsQueryParameter(final String name,
            final Predicate<String> predicate) {

        Objects.requireNonNull(name, "query param name");
        Objects.requireNonNull(predicate, "query param predicate");
        return and((req) -> req.queryParams()
                .all(name)
                .stream()
                .anyMatch(predicate));
    }

    /**
     * Accept requests only when the specified query parameter contains a given
     * value.
     *
     * @param name query parameter name
     * @param value expected query parameter value
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsQueryParameter(final String name,
            final String value) {

        Objects.requireNonNull(name, "query param name");
        Objects.requireNonNull(value, "query param value");
        return and((req) -> req.queryParams()
                .all(name)
                .stream()
                .anyMatch(value::equals));
    }

    /**
     * Accept request only when the specified cookie exists.
     *
     * @param name cookie name
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsCookie(final String name) {
        Objects.requireNonNull(name, "cookie name");
        return and((req) -> req.headers()
                .values("cookie")
                .stream()
                .anyMatch((c) -> c.startsWith(name + "=")));
    }

    /**
     * Accept requests only when the specified cookie is valid.
     *
     * @param name cookie name
     * @param predicate predicate to match the cookie value
     * @return new predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or predicate is null
     */
    public RequestPredicate containsCookie(final String name,
            final Predicate<String> predicate) {

        Objects.requireNonNull(name, "cookie name");
        Objects.requireNonNull(predicate, "cookie predicate");
        return and((req) -> req.headers()
                .cookies()
                .all(name)
                .stream()
                .anyMatch((c) -> predicate.test(c)));
    }

    /**
     * Accept requests only when the specified cookie contains a given value.
     *
     * @param name cookie name
     * @param value expected cookie value
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsCookie(final String name,
            final String value) {

        Objects.requireNonNull(name, "cookie name");
        Objects.requireNonNull(value, "cookie value");
        return and((req) -> req.headers()
                .cookies()
                .all(name)
                .stream()
                .anyMatch(value::equals));
    }

    /**
     * Accept requests only when it accepts any of the given content types.
     *
     * @param contentType the content types to test
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate accepts(final String... contentType) {
        Objects.requireNonNull(contentType, "content types");
        return and((req) ->
                Stream.of(contentType).anyMatch((mt) ->
                        req.headers().isAccepted(MediaType.parse(mt))));
    }

    /**
     * Only accept request that accepts any of the given content types.
     *
     * @param contentType the content types to test
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate accepts(final MediaType... contentType) {
        Objects.requireNonNull(contentType, "accepted media types");
        return and((req) ->
                Stream.of(contentType).anyMatch((mt) ->
                        req.headers().isAccepted(mt)));
    }

    /**
     * Only accept requests with any of the given content types.
     *
     * @param contentType Content type
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate hasContentType(final String... contentType) {
        Objects.requireNonNull(contentType, "accepted media types");
        return and((req) -> {
            Optional<MediaType> actualContentType = req.headers().contentType();
            return actualContentType.isPresent()
                    && Stream.of(contentType)
                        .anyMatch((mt) -> actualContentType.get()
                                .equals(MediaType.parse(mt)));
                });
    }

    /**
     * Only accept requests with any of the given content types.
     *
     * @param contentType Content type
     * @return composed predicate representing the logical expression of the previous
     * predicate expression <b>and</b> the logical expression implemented by this
     * method
     * @throws NullPointerException if the specified content type array is null
     */
    public RequestPredicate hasContentType(final MediaType... contentType) {
        Objects.requireNonNull(contentType, "content types");
        return and((req) -> {
            Optional<MediaType> actualContentType = req.headers().contentType();
            return actualContentType.isPresent()
                    && Stream.of(contentType)
                        .anyMatch((mt) -> actualContentType.get()
                                .equals(mt));
                });
    }

    /**
     * Creates new empty {@link RequestPredicate} instance.
     *
     * @return new empty predicate (accepts all requests).
     */
    public static RequestPredicate create() {
        return new RequestPredicate();
    }

    /**
     * A {@link Handler} that conditionally delegates to other {link Handler}
     * instances based on a {@link RequestPredicate}.
     *
     * There can be at most 2 handlers: a required one for matched requests and
     * an optional one non matched requests. If the handler for non matched
     * requests is not provided, such request will return a {@code 404} response.
     */
    public static class ConditionalHandler implements Handler {

        /**
         * The condition for the delegation.
         */
        private final RequestPredicate condition;

        /**
         * The {@link Handler} to use when the predicate matches.
         */
        private final Handler acceptHandler;

        /**
         * The {@link Handler} to use when the predicate does not match.
         */
        private final Handler declineHandler;

        /**
         * Create a new instance.
         * @param condition the predicate
         * @param acceptHandler the predicate to use when the predicate matches
         * @param declineHandler the predicate to use when the predicate does not
         * match.
         */
        private ConditionalHandler(final RequestPredicate condition,
                final Handler acceptHandler, final Handler declineHandler) {

            this.condition = condition;
            this.acceptHandler = acceptHandler;
            this.declineHandler = declineHandler == null
                    ? ((req, res) -> req.next()) : declineHandler;
        }

        /**
         * Create a new instance.
         * @param condition the predicate
         * @param acceptHandler the predicate to use when the predicate matches
         * match
         */
        private ConditionalHandler(final RequestPredicate condition,
                final Handler acceptHandler) {

            this(condition, acceptHandler, null);
        }

        @Override
        public void accept(ServerRequest req, ServerResponse res) {
            if (condition.test(req)) {
                acceptHandler.accept(req, res);
            } else {
                declineHandler.accept(req, res);
            }
        }

        /**
         * Set the {@link Handler} to use when the predicate does not match the
         * request.
         *
         * @param handler handler to use when the predicate does not match
         * @return created {@link Handler}
         */
        public Handler otherwise(final Handler handler) {
            return new ConditionalHandler(condition, acceptHandler, handler);
        }
    }

    /**
     * Recursive evaluation of a predicate chain.
     * @param currentValue the initial value
     * @param predicate the predicate to resolve the new value
     * @param request the server request
     * @return the evaluated value
     */
    private static boolean eval(final boolean currentValue,
            final RequestPredicate predicate, final ServerRequest request){

        boolean newValue = predicate.expr.eval(currentValue, request);
        if (predicate.next != null) {
            return eval(newValue, predicate.next, request);
        }
        return newValue;
    }

    /**
     * An expression is part of a chain of other expressions, it computes a value
     * based on an input object and the current value of the chain.
     * @param <T> input object type
     */
    @FunctionalInterface
    private interface Expression {

        /**
         * Evaluate this expression.
         * @param currentValue the current value
         * @param input the input object
         * @return the new value
         */
        boolean eval(boolean currentValue, ServerRequest input);
    }
}
