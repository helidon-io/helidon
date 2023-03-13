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
import io.helidon.common.http.PathMatchers;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

class HttpRulesTest {
    private final Handler handler = (req, res) -> res.send("done");

    @Test
    void HttpMethodShortcuts() {
        Map<Http.Method, FakeHttpRules> map = Map.of(Http.Method.GET,
                                                     (FakeHttpRules) new FakeHttpRules().get(handler),
                                                     Http.Method.POST,
                                                     (FakeHttpRules) new FakeHttpRules().post(handler),
                                                     Http.Method.PUT,
                                                     (FakeHttpRules) new FakeHttpRules().put(handler),
                                                     Http.Method.DELETE,
                                                     (FakeHttpRules) new FakeHttpRules().delete(handler),
                                                     Http.Method.HEAD,
                                                     (FakeHttpRules) new FakeHttpRules().head(handler),
                                                     Http.Method.OPTIONS,
                                                     (FakeHttpRules) new FakeHttpRules().options(handler),
                                                     Http.Method.TRACE,
                                                     (FakeHttpRules) new FakeHttpRules().trace(handler),
                                                     Http.Method.PATCH,
                                                     (FakeHttpRules) new FakeHttpRules().patch(handler));
        for (Map.Entry<Http.Method, FakeHttpRules> entry : map.entrySet()) {
            assertThat(entry.getValue().getPathPattern(), is(nullValue()));
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            assertThat(entry.getValue().getHandler(), is(handler));
        }
    }

    @Test
    void HttpMethodShortcutsWithPathPattern() {
        String pathPattern = "/test/*";
        Map<Http.Method, FakeHttpRules> map = Map.of(Http.Method.GET,
                                                     (FakeHttpRules) new FakeHttpRules().get(pathPattern, handler),
                                                     Http.Method.POST,
                                                     (FakeHttpRules) new FakeHttpRules().post(pathPattern, handler),
                                                     Http.Method.PUT,
                                                     (FakeHttpRules) new FakeHttpRules().put(pathPattern, handler),
                                                     Http.Method.DELETE,
                                                     (FakeHttpRules) new FakeHttpRules().delete(pathPattern, handler),
                                                     Http.Method.HEAD,
                                                     (FakeHttpRules) new FakeHttpRules().head(pathPattern, handler),
                                                     Http.Method.OPTIONS,
                                                     (FakeHttpRules) new FakeHttpRules().options(pathPattern, handler),
                                                     Http.Method.TRACE,
                                                     (FakeHttpRules) new FakeHttpRules().trace(pathPattern, handler),
                                                     Http.Method.PATCH,
                                                     (FakeHttpRules) new FakeHttpRules().patch(pathPattern, handler));
        for (Map.Entry<Http.Method, FakeHttpRules> entry : map.entrySet()) {
            assertThat(entry.getValue().getPathPattern(), is(pathPattern));
            assertThat(entry.getValue().getMethod(), is(entry.getKey()));
            assertThat(entry.getValue().getHandler(), is(handler));
        }
    }

    private static class FakeHttpRules implements HttpRules {
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
        public HttpRules register(Supplier<? extends HttpService>... service) {
            return null;
        }

        @Override
        public HttpRules register(String pathPattern, Supplier<? extends HttpService>... service) {
            return null;
        }

        @Override
        public HttpRules route(HttpRoute route) {
            return null;
        }

        @Override
        public HttpRules route(Http.Method method, String pathPattern, Handler handler) {
            this.method = method;
            this.pathPattern = pathPattern;
            this.handler = handler;
            return route(Http.Method.predicate(method), PathMatchers.create(pathPattern), handler);
        }

        @Override
        public HttpRules route(Http.Method method, Handler handler) {
            this.method = method;
            this.pathPattern = null;
            this.handler = handler;
            return route(HttpRoute.builder()
                                 .methods(method)
                                 .handler(handler));
        }
    }

}
