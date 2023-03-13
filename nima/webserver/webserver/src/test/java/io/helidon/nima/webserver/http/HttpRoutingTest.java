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

package io.helidon.nima.webserver.http;

import java.util.Map;
import java.util.function.Supplier;

import io.helidon.common.http.Http;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpRoutingTest {
    private final Handler handler = (req, res) -> res.send("done");

    @Test
    void HttpMethodShortcuts() {
        Map<Http.Method, FakeHttpRoutingBuilder> map = Map.of(Http.Method.GET,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .get(handler),
                                                              Http.Method.POST,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .post(handler),
                                                              Http.Method.PUT,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .put(handler),
                                                              Http.Method.DELETE,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .delete(handler),
                                                              Http.Method.HEAD,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .head(handler),
                                                              Http.Method.OPTIONS,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .options(handler),
                                                              Http.Method.TRACE,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .trace(handler),
                                                              Http.Method.PATCH,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .patch(handler));
        for (Map.Entry<Http.Method, FakeHttpRoutingBuilder> entry : map.entrySet()) {
            assertThat(entry.getValue().getPathPattern(), is(nullValue()));
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            assertThat(entry.getValue().getHandler(), is(handler));
        }
    }

    @Test
    void HttpMethodShortcutsWithPathPattern() {
        String pathPattern = "/test/*";
        Map<Http.Method, FakeHttpRoutingBuilder> map = Map.of(Http.Method.GET,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .get(pathPattern, handler),
                                                              Http.Method.POST,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .post(pathPattern, handler),
                                                              Http.Method.PUT,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .put(pathPattern, handler),
                                                              Http.Method.DELETE,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .delete(pathPattern, handler),
                                                              Http.Method.HEAD,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .head(pathPattern, handler),
                                                              Http.Method.OPTIONS,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .options(pathPattern, handler),
                                                              Http.Method.TRACE,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .trace(pathPattern, handler),
                                                              Http.Method.PATCH,
                                                              (FakeHttpRoutingBuilder) new FakeHttpRoutingBuilder()
                                                                      .patch(pathPattern, handler));
        for (Map.Entry<Http.Method, FakeHttpRoutingBuilder> entry : map.entrySet()) {
            assertThat(entry.getValue().getPathPattern(), is(pathPattern));
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            assertThat(entry.getValue().getHandler(), is(handler));
        }
    }

    private static class FakeHttpRoutingBuilder implements HttpRouting.Builder {
        private Http.Method method;
        private String pathPattern;
        private Handler handler;

        public Http.Method getMethod() {
            return method;
        }

        public String getPathPattern() {
            return pathPattern;
        }

        public Handler getHandler() {
            return handler;
        }

        @Override
        public HttpRouting.Builder register(Supplier<? extends HttpService>... service) {
            return null;
        }

        @Override
        public HttpRouting.Builder register(String path, Supplier<? extends HttpService>... service) {
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
        public HttpRouting.Builder route(Http.Method method, String pathPattern, Handler handler) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            return route(HttpRoute.builder().methods(method).path(pathPattern).handler(handler));
        }

        @Override
        public HttpRouting.Builder route(Http.Method method, Handler handler) {
            this.method = method;
            this.pathPattern = null;
            this.handler = handler;
            return route(HttpRoute.builder().methods(method).handler(handler));
        }
    }
}
