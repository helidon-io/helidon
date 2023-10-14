/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.http.Method;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HttpRoutingTest {
    private static final Handler handler = (req, res) -> res.send("done");

    // Functions that will be used to execute Routing http method shortcuts
    private static Function<String, FakeHttpRoutingBuilder> get = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().get(handler) : new FakeHttpRoutingBuilder().get(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> post = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().post(handler) : new FakeHttpRoutingBuilder().post(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> put = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().put(handler) : new FakeHttpRoutingBuilder().put(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> delete = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().delete(handler) : new FakeHttpRoutingBuilder().delete(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> head = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().head(handler) : new FakeHttpRoutingBuilder().head(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> options = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().options(handler) : new FakeHttpRoutingBuilder().options(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> trace = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().trace(handler) : new FakeHttpRoutingBuilder().trace(x, handler));
    private static Function<String, FakeHttpRoutingBuilder> patch = x ->
            (FakeHttpRoutingBuilder) (x == null ? new FakeHttpRoutingBuilder().patch(handler) : new FakeHttpRoutingBuilder().patch(x, handler));

    @ParameterizedTest
    @MethodSource("httpMethodShortcut")
    void testHttpMethodShortcut(Method method,
                                Function<String, FakeHttpRoutingBuilder> request) {
        FakeHttpRoutingBuilder rule = request.apply(null);
        assertThat(rule.getMethod(), is(method));
        assertThat(rule.getPathPattern(), is(nullValue()));
        assertThat(rule.getHandler(), is(handler));
    }

    @ParameterizedTest
    @MethodSource("httpMethodShortcutWithPathPattern")
    void testHttpMethodShortcutWithPathPattern(Method method,
                                               Function<String, FakeHttpRoutingBuilder> request,
                                               String pathPattern) {
        FakeHttpRoutingBuilder rule = request.apply(pathPattern);
        assertThat(rule.getMethod(), is(method));
        assertThat(rule.getPathPattern(), is(pathPattern));
        assertThat(rule.getHandler(), is(handler));

    }

    private static Stream<Arguments> httpMethodShortcut() {
        return Stream.of(
                arguments(Method.GET, get),
                arguments(Method.POST, post),
                arguments(Method.PUT, put),
                arguments(Method.DELETE, delete),
                arguments(Method.HEAD, head),
                arguments(Method.OPTIONS, options),
                arguments(Method.TRACE, trace),
                arguments(Method.PATCH, patch)
        );
    }

    private static Stream<Arguments> httpMethodShortcutWithPathPattern() {
        return Stream.of(
                arguments(Method.GET, get, "/get"),
                arguments(Method.POST, post, "/post"),
                arguments(Method.PUT, put, "/put"),
                arguments(Method.DELETE, delete, "/delete"),
                arguments(Method.HEAD, head, "/head"),
                arguments(Method.OPTIONS, options, "/options"),
                arguments(Method.TRACE, trace, "/trace"),
                arguments(Method.PATCH, patch, "/patch")
        );
    }

    private static class FakeHttpRoutingBuilder implements HttpRouting.Builder {
        private Method method;
        private String pathPattern;
        private Handler handler;

        public Method getMethod() {
            return method;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public Handler getHandler() {
            return handler;
        }

        @Override
        public HttpRouting.Builder register(HttpService... service) {
            return null;
        }

        @Override
        public HttpRouting.Builder register(String path, HttpService... service) {
            return null;
        }

        @Override
        public HttpRouting.Builder route(HttpRoute route) {
            return null;
        }

        @Override
        public HttpRouting.Builder addFilter(Filter filter) {
            return null;
        }

        @Override
        public HttpRouting.Builder addFeature(Supplier<? extends HttpFeature> feature) {
            return null;
        }

        @Override
        public <T extends Throwable> HttpRouting.Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
            return null;
        }

        @Override
        public HttpRouting.Builder maxReRouteCount(int maxReRouteCount) {
            return null;
        }

        @Override
        public HttpRouting.Builder security(HttpSecurity security) {
            return null;
        }

        @Override
        public HttpRouting build() {
            return null;
        }

        @Override
        public HttpRouting.Builder route(Method method, String pathPattern, Handler handler) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            return route(HttpRoute.builder().methods(method).path(pathPattern).handler(handler));
        }

        @Override
        public HttpRouting.Builder route(Method method, Handler handler) {
            this.method = method;
            this.pathPattern = null;
            this.handler = handler;
            return route(HttpRoute.builder().methods(method).handler(handler));
        }

        @Override
        public HttpRouting.Builder copy() {
            return this;
        }
    }
}
