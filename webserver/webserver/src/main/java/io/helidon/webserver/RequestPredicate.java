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

import java.util.function.Predicate;

import io.helidon.common.http.MediaType;

/**
 * Fluent API to define complex request conditions. Suitable for definition of more complex routing in {@link Routing.Rules}.
 * <p>
 * Construct condition using {@link #whenRequest()} method and fluent API. Then create request handler using
 * {@link #thenApply(Handler)})} method.
 * <h3>Examples</h3>
 * <p>Creates {@link Handler} which executes provided logic only if request contains {@code foo} header and accepts
 * {@code text/plain}. If not, then it calls {@link ServerRequest#next() req.next()}.
 * <pre>{@code
 * RequestPredicate.whenRequest()
 *                 .containsHeader("foo")
 *                 .accepts(MediaType.TEXT_PLAIN)
 *                 .thenApply((req, resp) -> {
 *                     // Some logic
 *                 });
 * }</pre>
 * <p>Creates {@link Handler} which executes provided logic only if request contains {@code foo} header. If not, then it executes
 * '<i>otherwise logic</i>' which, in this case, throws a {@code RuntimeException}.
 * <pre>{@code
 * RequestPredicate.whenRequest()
 *                 .containsHeader("foo")
 *                 .thenApply((req, resp) -> {
 *                     // Some logic
 *                 })
 *                 .otherwise(req, resp) -> {
 *                     throw new RuntimeException("Missing 'foo' header!");
 *                 });
 * }</pre>
 */
public interface RequestPredicate extends Predicate<ServerRequest> {

    /**
     * Accepts only requests with one of specified HTTP methods.
     *
     * @param methodNames Acceptable method names.
     * @return New enhanced instance.
     */
    RequestPredicate isOfMethod(String... methodNames);

    /**
     * Accepts only requests with specified header name.
     *
     * @param headerName Header name.
     * @return New enhanced instance.
     */
    RequestPredicate containsHeader(String headerName);

    /**
     * Accepts only requests with specified header containing valid value.
     * <p>
     * If request contains more then one header instance, then value predicate is called for all instances. Request is accepted
     * if ANY predicate call returns {@code true}.
     *
     * @param headerName Header name.
     * @param headerValuePredicate Predicate for header value. Is called for all header values.
     * @return New enhanced instance.
     */
    RequestPredicate containsHeader(String headerName, Predicate<String> headerValuePredicate);

    /**
     * Accepts only requests with specified header containing valid value.
     * <p>
     * If request contains more then one header instance, then value is tested for all instances. Request is accepted
     * if ANY value equals to provided.
     *
     * @param headerName Header name.
     * @param value Expected header value.
     * @return New enhanced instance.
     */
    RequestPredicate containsHeader(String headerName, String value);

    /**
     * Accepts only requests with specified query parameter.
     *
     * @param queryParameterName Query parameter
     * @return New enhanced instance.
     */
    RequestPredicate containsQueryParameter(String queryParameterName);

    /**
     * Accepts only requests with specified query parameter.
     *
     * @param queryParameterName Query parameter name.
     * @param parameterValuePredicate Predicate for a parameter value.
     * @return New enhanced instance.
     */
    RequestPredicate containsQueryParameter(String queryParameterName, Predicate<String> parameterValuePredicate);

    /**
     * Accepts only requests with specified query parameter.
     *
     * @param queryParameterName Query parameter name.
     * @param value Expected value.
     * @return New enhanced instance.
     */
    RequestPredicate containsQueryParameter(String queryParameterName, String value);

    /**
     * Accepts only requests with specified cookie name.
     *
     * @param cookieName Cookie name.
     * @return New enhanced instance.
     */
    RequestPredicate containsCookie(String cookieName);

    /**
     * Accepts only requests with specified cookie containing valid value.
     *
     * @param cookieName Header name.
     * @param cookieValuePredicate Predicate for a cookie value.
     * @return New enhanced instance.
     */
    RequestPredicate containsCookie(String cookieName, Predicate<String> cookieValuePredicate);

    /**
     * Accepts only requests with specified cookie containing valid value.
     *
     * @param cookieName Header name.
     * @param value Predicate for a cookie value.
     * @return New enhanced instance.
     */
    RequestPredicate containsCookie(String cookieName, String value);

    /**
     * Accepts only requests accepting any of specified content types.
     *
     * @param contentType Content type.
     * @return New enhanced instance.
     */
    RequestPredicate accepts(String... contentType);

    /**
     * Accepts only requests accepting any of specified content types.
     *
     * @param contentType Content type.
     * @return New enhanced instance.
     */
    RequestPredicate accepts(MediaType... contentType);

    /**
     * Accepts only requests of any specified content types.
     *
     * @param contentType Content type.
     * @return New enhanced instance.
     */
    RequestPredicate hasContentType(String... contentType);

    /**
     * Accepts by free form condition. Equivalent method for {@link #and(Predicate)}.
     *
     * @param requestPredicate A request predicate.
     * @return New enhanced instance.
     */
    RequestPredicate is(Predicate<? super ServerRequest> requestPredicate);

    /**
     * Creates request-response handler/filter which calls provided handler only if this predicate accepts provided request,
     * otherwise call {@link ServerRequest#next()} method.
     *
     * @param handler to apply if this instance accepts provided request.
     * @return a conditional handler.
     */
    ConditionalHandler thenApply(Handler handler);

    @Override
    default RequestPredicate and(Predicate<? super ServerRequest> other) {
        throw new UnsupportedOperationException();
    }

    @Override
    default RequestPredicate negate() {
        throw new UnsupportedOperationException();
    }

    @Override
    default RequestPredicate or(Predicate<? super ServerRequest> other) {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates new empty instance {@link RequestPredicate} instance. It can be used as a base for fluent construction of
     * complex predicate.
     *
     * @return New empty instance (accepts all requests).
     */
    static RequestPredicate whenRequest() {
        throw new UnsupportedOperationException();
    }

    /**
     * Combines several provided predicates in short circuit OR manner.
     *
     * @param predicates to combine.
     * @return Combined predicate.
     */
    static RequestPredicate any(RequestPredicate... predicates) {
        throw new UnsupportedOperationException();
    }

    /**
     * A {@link Handler} which executes provided logic only if provided condition is satisfied.
     * An instance can be created using {@link RequestPredicate#thenApply(Handler)} method.
     */
    class ConditionalHandler implements Handler {

        private final RequestPredicate condition;
        private final Handler acceptHandler;
        private final Handler declineHandler;

        private ConditionalHandler(RequestPredicate condition, Handler acceptHandler, Handler declineHandler) {
            this.condition = condition;
            this.acceptHandler = acceptHandler;
            this.declineHandler = declineHandler == null ? ((req, res) -> req.next()) : declineHandler;
        }

        ConditionalHandler(RequestPredicate condition, Handler acceptHandler) {
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
         * Creates new {@link Handler} instance which executes this handler if condition was satisfied, <i>otherwise</i>
         * executes provided {@code handler}.
         *
         * @param handler a handler which is executed when condition was not satisfied.
         * @return a new handler.
         */
        public Handler otherwise(Handler handler) {
            return new ConditionalHandler(condition.negate(), handler, acceptHandler);
        }
    }
}
