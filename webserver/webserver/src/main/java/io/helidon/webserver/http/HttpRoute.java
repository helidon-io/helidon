/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

package io.helidon.webserver.http;

import java.util.Objects;
import java.util.function.Predicate;

import io.helidon.http.Http;
import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.Route;

/**
 * A basic HTTP route (should be usable by ANY HTTP protocol version).
 */
public interface HttpRoute extends Route {
    /**
     * Builder to build a new HTTP route.
     *
     * @return builder
     */
    static HttpRouteImpl.Builder builder() {
        return new Builder();
    }

    /**
     * Whether this route accept the provided request.
     *
     * @param prologue prologue of the request
     * @return result of the validation
     * @see io.helidon.http.PathMatchers.MatchResult#notAccepted()
     */
    PathMatchers.MatchResult accepts(HttpPrologue prologue);

    /**
     * Handler of this route.
     *
     * @return handler
     */
    Handler handler();

    /**
     * Fluent API builder for {@link HttpRoute}.
     */
    class Builder implements io.helidon.common.Builder<Builder, HttpRoute> {
        private Predicate<Http.Method> methodPredicate = Http.Method.predicate();
        private PathMatcher pathMatcher = PathMatchers.any();
        private Handler handler;

        private Builder() {
        }

        @Override
        public HttpRoute build() {
            Objects.requireNonNull(handler, "Handler must be provided");
            return new HttpRouteImpl(this);
        }

        /**
         * HTTP methods this route should handle.
         *
         * @param methods methods to handle
         * @return updated builder
         */
        public Builder methods(Http.Method... methods) {
            return methods(Http.Method.predicate(methods));
        }

        /**
         * Method predicate to use.
         *
         * @param methodPredicate method predicate
         * @return updated builder
         */
        public Builder methods(Predicate<Http.Method> methodPredicate) {
            this.methodPredicate = methodPredicate;
            return this;
        }

        /**
         * Path pattern to handle.
         *
         * @param pathPattern path pattern
         * @return updated builder
         */
        public Builder path(String pathPattern) {
            return this.path(PathMatchers.create(pathPattern));
        }

        /**
         * Path matcher to handle path.
         *
         * @param pathMatcher path matcher
         * @return updated builder
         */
        public Builder path(PathMatcher pathMatcher) {
            this.pathMatcher = pathMatcher;
            return this;
        }

        /**
         * Handler to use.
         *
         * @param handler handler
         * @return updated builder
         */
        public Builder handler(Handler handler) {
            this.handler = handler;
            return this;
        }

        Predicate<Http.Method> methodPredicate() {
            return methodPredicate;
        }

        PathMatcher pathPredicate() {
            return pathMatcher;
        }

        Handler handler() {
            return handler;
        }
    }
}
