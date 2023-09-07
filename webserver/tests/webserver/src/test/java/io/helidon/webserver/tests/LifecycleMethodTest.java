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

package io.helidon.webserver.tests;

import java.util.concurrent.atomic.AtomicInteger;

import io.helidon.http.HttpPrologue;
import io.helidon.http.PathMatchers;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.FilterChain;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.RoutingRequest;
import io.helidon.webserver.http.RoutingResponse;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class LifecycleMethodTest {
    private static final TestFeature FEATURE = new TestFeature();
    private static final TestService SERVICE = new TestService();
    private static final TestRoute ROUTE = new TestRoute();
    private static final TestHandler HANDLER = new TestHandler();
    private static final TestFilter FILTER = new TestFilter();

    static void setUpRoute(HttpRouting.Builder http) {
        http.addFeature(FEATURE)
                .register(SERVICE)
                .route(ROUTE)
                .get("/handler", HANDLER)
                .addFilter(FILTER);

    }

    @BeforeEach
    void resetCounter() {
        FEATURE.reset();
        SERVICE.reset();
        ROUTE.reset();
        HANDLER.reset();
        FILTER.reset();
    }

    @RepeatedTest(5)
    void testLifecycleMethodsCalled() {
        WebServer server = WebServer.builder()
                .routing(LifecycleMethodTest::setUpRoute)
                .build()
                .start();
        server.start();
        validateAfterStart("Feature", FEATURE);
        validateAfterStart("Service", SERVICE);
        validateAfterStart("Route", ROUTE);
        validateAfterStart("Handler", HANDLER);
        validateAfterStart("Filter", FILTER);

        server.stop();
        validateAfterStop("Feature", FEATURE);
        validateAfterStop("Service", SERVICE);
        validateAfterStop("Route", ROUTE);
        validateAfterStop("Handler", HANDLER);
        validateAfterStop("Filter", FILTER);
    }

    static void validateAfterStart(String name, Validated component) {
        assertThat(name + " before start should have been called on server startup", component.starts(), is(1));
        assertThat(name + " after stop should not have been called on server startup", component.stops(), is(0));
    }

    static void validateAfterStop(String name, Validated component) {
        assertThat(name + " before start should have been called on server startup", component.starts(), is(1));
        assertThat(name + " after stop should have been called on server shutdown", component.stops(), is(1));
    }

    private interface Validated {
        int starts();

        int stops();

        void reset();
    }

    static final class TestFilter implements Filter, Validated {
        private final AtomicInteger beforeStart = new AtomicInteger();
        private final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void filter(FilterChain chain, RoutingRequest req, RoutingResponse res) {
            chain.proceed();
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        @Override
        public int starts() {
            return beforeStart.get();
        }

        @Override
        public int stops() {
            return afterStop.get();
        }

        @Override
        public void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }

    static final class TestHandler implements Handler, Validated {
        private final AtomicInteger beforeStart = new AtomicInteger();
        private final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void handle(ServerRequest req, ServerResponse res) {
            res.send("handler");
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        @Override
        public int starts() {
            return beforeStart.get();
        }

        @Override
        public int stops() {
            return afterStop.get();
        }

        @Override
        public void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }

    static final class TestRoute implements HttpRoute, Validated {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
            return new PathMatchers.MatchResult(false, null);
        }

        @Override
        public Handler handler() {
            return (req, res) -> res.send("route");
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        @Override
        public int starts() {
            return beforeStart.get();
        }

        @Override
        public int stops() {
            return afterStop.get();
        }

        @Override
        public void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }

    static final class TestService implements HttpService, Validated {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void routing(HttpRules rules) {
            rules.get("/service", (req, res) -> res.send("service"));
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        @Override
        public int starts() {
            return beforeStart.get();
        }

        @Override
        public int stops() {
            return afterStop.get();
        }

        @Override
        public void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }

    static final class TestFeature implements HttpFeature, Validated {
        final AtomicInteger beforeStart = new AtomicInteger();
        final AtomicInteger afterStop = new AtomicInteger();

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.get("/feature", (req, res) -> res.send("feature"));
        }

        @Override
        public void beforeStart() {
            beforeStart.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStop.incrementAndGet();
        }

        @Override
        public int starts() {
            return beforeStart.get();
        }

        @Override
        public int stops() {
            return afterStop.get();
        }

        @Override
        public void reset() {
            beforeStart.set(0);
            afterStop.set(0);
        }
    }
}
