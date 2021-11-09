/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.helidon.common.http.Http.Method;
import io.helidon.common.http.MediaType;

/**
 * Fluent API that allows to create chains of request conditions for composing
 * logical expressions to match requests.
 *
 *<p>
 * A new expression can be created using the {@link #create()} method. This method
 * will initialize an expression with an empty condition that will match any
 * request.
 *<p>
 * Conditions are added to the expression by chaining method invocations and form
 * a logical <b>AND</b> expression. Each method invocation will return a different
 * instance representing the last condition in the expression. Each instance can
 * represent only one condition, invoking a method representing a condition more
 * than once per instance will throw an {@link IllegalStateException}.
 *<p>
 * The expression can be evaluated against a request using the
 * {@link #test(ServerRequest)} method, or a {@link ConditionalHandler} can be
 * used to evaluate the expression and delegate to other handlers based on the
 * result of the evaluation.
 *<p>
 * The {@link #thenApply(Handler)} method can be invoked on an expression to create
 * a {@link ConditionalHandler}.
 *<p>
 * The handler to be used for matching requests is passed as parameter to
 * {@link #thenApply(Handler)} and the handler to be used for requests that do not
 * match can be specified using {@link ConditionalHandler#otherwise(Handler) }.
 * <h2>Examples</h2>
 * <p>
 * Invoke a {@link Handler} only when the request contains a header name {@code foo}
 * and accepts {@code text/plain}, otherwise return a response with {@code 404} code.
 * <pre>{@code
 * RequestPredicate.create()
 *                 .containsHeader("foo")
 *                 .accepts(MediaType.TEXT_PLAIN)
 *                 .thenApply((req, resp) -> {
 *                     // handler logic
 *                 });
 * }</pre>
 * <p>
 * Invoke a {@link Handler} only when the request contains a header named {@code foo}
 * otherwise invoke another handler that throws an exception.
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
     * A condition that returns the current value.
     */
    private static final Condition EMPTY_CONDITION = (a, b) -> a;

    /**
     * The first predicate in the predicate chain.
     */
    private final RequestPredicate first;

    /**
     * The next predicate in the predicate chain.
     */
    private volatile RequestPredicate next;

    /**
     * The condition for this predicate.
     */
    private final Condition condition;

    /**
     * Create an empty predicate.
     */
    private RequestPredicate(){
        this.first = this;
        this.next = null;
        this.condition = EMPTY_CONDITION;
    }

    /**
     * Create a composed predicate with the given condition.
     * @param first the first predicate in the chain
     * @param expr the condition for the new predicate
     */
    private RequestPredicate(final RequestPredicate first,
            final Condition cond){

        this.first = first;
        this.next = null;
        this.condition = cond;
    }

    /**
     * Create a composed predicate and add it in the predicate chain.
     * @param newCondition the condition for the new predicate
     * @throws IllegalStateException if the next condition is already set
     * @return the created predicate
     */
    private RequestPredicate nextCondition(final Condition newCondition){
        if (next != null) {
            throw new IllegalStateException("next predicate already set");
        }
        this.next = new RequestPredicate(this.first, newCondition);
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
     * Evaluate this predicate.
     * @param request the server request
     * @return the computed value
     */
    public boolean test(final ServerRequest request) {
        return eval(/* initial value */ true, this.first, request);
    }

    /**
     * Returns a composed predicate that represents a logical AND expression
     * between this predicate and another predicate.
     *
     * @param predicate predicate to compose with
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     */
    public RequestPredicate and(final Predicate<ServerRequest> predicate) {
        return nextCondition((exprVal, req) -> exprVal && predicate.test(req));
    }

    /**
     * Returns a composed predicate that represents a logical OR expression
     * between this predicate and another predicate.
     *
     * @param predicate predicate that compute the new value
     * @return composed predicate representing the logical expression between
     * this predicate <b>OR</b> the provided predicate
     */
    public RequestPredicate or(final Predicate<ServerRequest> predicate) {
        return nextCondition((exprVal, req) -> exprVal || predicate.test(req));
    }

    /**
     * Return a predicate that represents the logical negation of this predicate.
     * @return new predicate that represents the logical negation of this predicate.
     */
    public RequestPredicate negate() {
        return nextCondition((exprVal, req) -> !exprVal);
    }

    /**
     * Accepts only requests with one of specified HTTP methods.
     *
     * @param methods Acceptable method names
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified methods array is null
     */
    public RequestPredicate isOfMethod(final String... methods) {
        Objects.requireNonNull(methods, "methods");
        return and((req) -> Stream.of(methods)
                        .map(String::toUpperCase)
                        .anyMatch(req.method().name()::equals));
    }

    /**
     * Accepts only requests with one of specified HTTP methods.
     *
     * @param methods Acceptable method names
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the methods type array is null
     */
    public RequestPredicate isOfMethod(final Method... methods) {
        Objects.requireNonNull(methods, "methods");
        return and((req) -> Stream.of(methods)
                        .map(Method::name)
                        .anyMatch(req.method().name()::equals));
    }

    /**
     * Accept requests only when the specified header name exists.
     *
     * @param name header name
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsHeader(final String name) {
        return containsHeader(name, (c) -> true);
    }

    /**
     * Accept requests only when the specified header contains a given value.
     *
     * If the request contains more then one header, it will be accepted
     * if <b>any</b> of the values is equal to the provided value.
     *
     * @param name header name
     * @param value the expected header value
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsHeader(final String name,
            final String value) {

        Objects.requireNonNull(value, "header value");
        return containsHeader(name, value::equals);
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
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name or predicate is null
     */
    public RequestPredicate containsHeader(final String name,
            final Predicate<String> predicate) {

        Objects.requireNonNull(name, "header name");
        Objects.requireNonNull(predicate, "header predicate");
        return and((req) -> req.headers()
                .value(name)
                .filter(predicate)
                .isPresent());
    }

    /**
     * Accept requests only when the specified query parameter exists.
     *
     * @param name query parameter name
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsQueryParameter(final String name) {
        return containsQueryParameter(name, (c) -> true);
    }

    /**
     * Accept requests only when the specified query parameter contains a given
     * value.
     *
     * @param name query parameter name
     * @param value expected query parameter value
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsQueryParameter(final String name,
            final String value) {

        Objects.requireNonNull(value, "query param value");
        return containsQueryParameter(name, value::equals);
    }

    /**
     * Accept requests only when the specified query parameter is valid.
     *
     * @param name query parameter name
     * @param predicate to match the query parameter value
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
     * Accept request only when the specified cookie exists.
     *
     * @param name cookie name
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name is null
     */
    public RequestPredicate containsCookie(final String name) {
        return containsCookie(name, (c) -> true);
    }

    /**
     * Accept requests only when the specified cookie contains a given value.
     *
     * @param name cookie name
     * @param value expected cookie value
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
     * @throws NullPointerException if the specified name or value is null
     */
    public RequestPredicate containsCookie(final String name,
            final String value) {

        Objects.requireNonNull(value, "cookie value");
        return containsCookie(name, value::equals);
    }

    /**
     * Accept requests only when the specified cookie is valid.
     *
     * @param name cookie name
     * @param predicate predicate to match the cookie value
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
                .anyMatch(predicate));
    }

    /**
     * Accept requests only when it accepts any of the given content types.
     *
     * @param contentType the content types to test
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
     * @return composed predicate representing the logical expression between
     * this predicate <b>AND</b> the provided predicate
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
     * A {@link Handler} that conditionally delegates to other {@link Handler}
     * instances based on a {@link RequestPredicate}.
     *
     * There can be at most 2 handlers: a required one for matched requests and
     * an optional one for requests that are not matched. If the handler for non
     * matched requests is not provided, such request will return a {@code 404}
     * response.
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

        boolean newValue = predicate.condition.eval(currentValue, request);
        if (predicate.next != null) {
            return eval(newValue, predicate.next, request);
        }
        return newValue;
    }

    /**
     * A condition represents some logic that evaluates a {@code boolean}
     * value based on a current {@code boolean} value and an input object.
     */
    @FunctionalInterface
    private interface Condition {

        /**
         * Evaluate this condition as part of a logical expression.
         * @param currentValue the current value of the expression
         * @param request the input object
         * @return the new value
         */
        boolean eval(boolean currentValue, ServerRequest request);
    }
}
