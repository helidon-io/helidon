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
import java.util.stream.Stream;

import io.helidon.http.Method;
import io.helidon.http.PathMatchers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class HttpRulesTest {
    private static final Handler handler = (req, res) -> res.send("done");

    // Functions that will be used to execute Rule http method shortcuts
    private static Function<String, FakeHttpRules> get = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().get(handler) : new FakeHttpRules().get(x, handler));
    private static Function<String, FakeHttpRules> post = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().post(handler) : new FakeHttpRules().post(x, handler));
    private static Function<String, FakeHttpRules> put = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().put(handler) : new FakeHttpRules().put(x, handler));
    private static Function<String, FakeHttpRules> delete = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().delete(handler) : new FakeHttpRules().delete(x, handler));
    private static Function<String, FakeHttpRules> head = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().head(handler) : new FakeHttpRules().head(x, handler));
    private static Function<String, FakeHttpRules> options = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().options(handler) : new FakeHttpRules().options(x, handler));
    private static Function<String, FakeHttpRules> trace = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().trace(handler) : new FakeHttpRules().trace(x, handler));
    private static Function<String, FakeHttpRules> patch = x ->
            (FakeHttpRules) (x == null ? new FakeHttpRules().patch(handler) : new FakeHttpRules().patch(x, handler));

    @ParameterizedTest
    @MethodSource("httpMethodShortcut")
    void testHttpMethodShortcut(Method method,
                                Function<String, FakeHttpRules> request) {
        FakeHttpRules rule = request.apply(null);
        assertThat(rule.getMethod(), is(method));
        assertThat(rule.getPathPattern(), is(nullValue()));
        assertThat(rule.getHandler(), is(handler));
    }

    @ParameterizedTest
    @MethodSource("httpMethodShortcutWithPathPattern")
    void testHttpMethodShortcutWithPathPattern(Method method,
                                               Function<String, FakeHttpRules> request,
                                               String pathPattern) {
        FakeHttpRules rule = request.apply(pathPattern);
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

    private static class FakeHttpRules implements HttpRules {
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
        public HttpRules register(HttpService... service) {
            return null;
        }

        @Override
        public HttpRules register(String pathPattern, HttpService... service) {
            return null;
        }

        @Override
        public HttpRules route(HttpRoute route) {
            return null;
        }

        @Override
        public HttpRules route(Method method, String pathPattern, Handler handler) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            return route(Method.predicate(method), PathMatchers.create(pathPattern), handler);
        }

        @Override
        public HttpRules route(Method method, Handler handler) {
            this.method = method;
            this.pathPattern = null;
            this.handler = handler;
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .handler(handler));
        }
    }

}
