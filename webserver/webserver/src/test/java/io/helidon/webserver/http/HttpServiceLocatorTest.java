/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.uri.UriFragment;
import io.helidon.common.uri.UriPath;
import io.helidon.common.uri.UriQuery;
import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatchers;
import io.helidon.http.RoutedPath;
import io.helidon.http.ServerRequestHeaders;
import io.helidon.http.Status;
import io.helidon.http.media.ReadableEntityBase;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.ListenerContext;
import io.helidon.webserver.WebServer;

import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HttpServiceLocatorTest {

    @Test
    void testLocatedServiceOwnsChildRoutes() throws Exception {
        var pipe = new ItemService("pipe");
        var manhole = new ItemService("manhole");
        var services = Map.of("pipe", pipe, "manhole", manhole);
        var locatedItem = new AtomicReference<String>();

        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> {
                    String item = request.path().pathParameters().first("item").orElseThrow();
                    locatedItem.set(item);
                    return Optional.ofNullable(services.get(item));
                })
                .build();

        var pipeInvocation = RoutingInvocation.create("/pipe/123");
        pipeInvocation.route(routing);
        assertThat(locatedItem.get(), is("pipe"));
        assertThat(pipeInvocation.entity(), is("pipe:pipe:123:/{item}/{id}"));

        var manholeInvocation = RoutingInvocation.create("/manhole/456");
        manholeInvocation.route(routing);
        assertThat(locatedItem.get(), is("manhole"));
        assertThat(manholeInvocation.entity(), is("manhole:manhole:456:/{item}/{id}"));
    }

    @Test
    void testServiceLambdaRegisterRemainsService() throws Exception {
        HttpRouting routing = HttpRouting.builder()
                .register(rules -> rules.get("/hello", (req, res) -> res.send("ok")))
                .register("/nested", rules -> rules.get("/hello", (req, res) -> res.send("nested")))
                .registerLocator("/missing", request -> Optional.empty())
                .build();

        var invocation = RoutingInvocation.create("/hello");
        invocation.route(routing);
        assertThat(invocation.entity(), is("ok"));

        invocation = RoutingInvocation.create("/nested/hello");
        invocation.route(routing);
        assertThat(invocation.entity(), is("nested"));
    }

    @Test
    void testNestedLocatorPreservesParentMatchingPattern() throws Exception {
        var located = new ItemService("nested");
        HttpService tenant = rules -> rules.registerLocator("/{item}", request -> Optional.of(located));

        HttpRouting routing = HttpRouting.builder()
                .register("/{tenant}", tenant)
                .build();

        var invocation = RoutingInvocation.create("/t1/pipe/123");
        invocation.route(routing);

        assertThat(invocation.entity(), is("nested:pipe:123:/{tenant}/{item}/{id}"));
    }

    @Test
    void testEmptyLocatorFallsThrough() throws Exception {
        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.empty())
                .get("/{item}/{id}", (req, res) -> res.send("fallback:" + req.path().pathParameters().first("item").get()))
                .build();

        var invocation = RoutingInvocation.create("/unknown/123");
        invocation.route(routing);

        assertThat(invocation.entity(), is("fallback:unknown"));
    }

    @Test
    void testEmptyLocatorRestoresPathWhenNoRouteMatches() throws Exception {
        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.empty())
                .build();

        var invocation = RoutingInvocation.create("/unknown/123");

        invocation.route(routing);
        assertThat(invocation.path(), nullValue());
    }

    @Test
    void testLocatedServiceRestoresPathWhenChildDoesNotMatch() throws Exception {
        HttpService service = rules -> rules.get("/other", (req, res) -> res.send("located"));
        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.of(service))
                .build();

        var invocation = RoutingInvocation.create("/unknown/123");

        invocation.route(routing);
        assertThat(invocation.path(), nullValue());
    }

    @Test
    void testNextContinuesInLocatedServiceThenFallsThrough() throws Exception {
        HttpService serviceWithNext = rules -> rules.get("/{id}", (req, res) -> res.next())
                .get("/{id}", (req, res) -> res.send("located"));

        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.of(serviceWithNext))
                .get("/{item}/{id}", (req, res) -> res.send("fallback"))
                .build();

        var invocation = RoutingInvocation.create("/pipe/123");
        invocation.route(routing);

        assertThat(invocation.entity(), is("located"));

        HttpService serviceWithOnlyNext = rules -> rules.get("/{id}", (req, res) -> res.next());
        routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.of(serviceWithOnlyNext))
                .get("/{item}/{id}", (req, res) -> res.send("fallback"))
                .build();

        invocation = RoutingInvocation.create("/pipe/123");
        invocation.route(routing);

        assertThat(invocation.entity(), is("fallback"));
    }

    @Test
    void testRerouteRestartsFromRoot() throws Exception {
        HttpService reroutingService = rules -> rules.get("/{id}", (req, res) -> res.reroute("/manhole/456"));
        HttpService manhole = rules -> rules.get("/{id}", (req, res) -> res.send("manhole:"
                + req.path().pathParameters().first("id").get()));
        Map<String, HttpService> services = Map.of("pipe", reroutingService, "manhole", manhole);

        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", request -> Optional.ofNullable(services.get(
                        request.path().pathParameters().first("item").orElseThrow())))
                .build();

        var invocation = RoutingInvocation.create("/pipe/123");
        invocation.route(routing);

        assertThat(invocation.entity(), is("manhole:456"));
    }

    @Test
    void testLocatedServiceLifecycleAndIdentityCache() throws Exception {
        var service = new LifecycleService();
        var locator = new LifecycleLocator(service);
        WebServer webServer = mock(WebServer.class);

        HttpRouting routing = HttpRouting.builder()
                .registerLocator("/{item}", locator)
                .build();

        routing.beforeStart();
        routing.afterStart(webServer);

        var invocation = RoutingInvocation.create("/pipe/123");
        invocation.route(routing);

        invocation = RoutingInvocation.create("/pipe/456");
        invocation.route(routing);

        routing.afterStop();

        assertThat(locator.locateCount.get(), is(2));
        assertThat(locator.beforeStartCount.get(), is(1));
        assertThat(locator.afterStartCount.get(), is(1));
        assertThat(locator.afterStopCount.get(), is(1));
        assertThat(service.routingCount.get(), is(1));
        assertThat(service.beforeStartCount.get(), is(1));
        assertThat(service.afterStartCount.get(), is(1));
        assertThat(service.afterStopCount.get(), is(1));
    }

    @Test
    void testLocatedServiceAfterStartFailureRunsAfterStop() {
        var service = new LifecycleService("located", true, false);
        var locator = new LifecycleLocator(service);
        var route = locatorRoute(locator);

        route.beforeStart();
        route.afterStart(mock(WebServer.class));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> locate(route));

        assertThat(containsMessage(failure, "afterStart failed located"), is(true));
        assertThat(service.routingCount.get(), is(1));
        assertThat(service.beforeStartCount.get(), is(1));
        assertThat(service.afterStartCount.get(), is(1));
        assertThat(service.afterStopCount.get(), is(1));
    }

    @Test
    void testLocatedServiceBeforeStartFailureRunsAfterStop() {
        var service = new LifecycleService("located", true, false, false);
        var locator = new LifecycleLocator(service);
        var route = locatorRoute(locator);

        route.beforeStart();
        route.afterStart(mock(WebServer.class));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> locate(route));

        assertThat(containsMessage(failure, "beforeStart failed located"), is(true));
        assertThat(service.routingCount.get(), is(1));
        assertThat(service.beforeStartCount.get(), is(1));
        assertThat(service.afterStartCount.get(), is(0));
        assertThat(service.afterStopCount.get(), is(1));
    }

    @Test
    void testAfterStopContinuesAcrossLocatorAndLocatedServiceFailures() {
        var first = new LifecycleService("first", false, true);
        var second = new LifecycleService("second");
        var locator = new SwitchingLocator(first, true);
        var route = locatorRoute(locator);

        route.beforeStart();
        route.afterStart(mock(WebServer.class));
        locate(route);
        locator.service(second);
        locate(route);

        RuntimeException failure = assertThrows(RuntimeException.class, route::afterStop);

        assertThat(containsMessage(failure, "afterStop failed locator"), is(true));
        assertThat(containsMessage(failure, "afterStop failed first"), is(true));
        assertThat(first.afterStopCount.get(), is(1));
        assertThat(second.afterStopCount.get(), is(1));
    }

    @Test
    void testAfterStopClearsLocatedServiceCache() {
        var service = new LifecycleService();
        var locator = new LifecycleLocator(service);
        var route = locatorRoute(locator);
        WebServer webServer = mock(WebServer.class);

        route.beforeStart();
        route.afterStart(webServer);
        locate(route);
        route.afterStop();

        route.beforeStart();
        route.afterStart(webServer);
        locate(route);

        assertThat(service.routingCount.get(), is(2));
        assertThat(service.beforeStartCount.get(), is(2));
        assertThat(service.afterStartCount.get(), is(2));
        assertThat(service.afterStopCount.get(), is(1));
    }

    @Test
    void testLocatedServiceCacheSizeIsBounded() {
        var first = new LifecycleService("first");
        var second = new LifecycleService("second");
        var locator = new SwitchingLocator(first, false, 1);
        var route = locatorRoute(locator);

        locate(route);
        locator.service(second);

        HttpException failure = assertThrows(HttpException.class, () -> locate(route));

        assertThat(failure.status(), is(Status.SERVICE_UNAVAILABLE_503));
        assertThat(first.routingCount.get(), is(1));
        assertThat(second.routingCount.get(), is(0));
    }

    @Test
    void testCachedLocatedServiceDoesNotWaitForColdServiceCreation() throws Exception {
        var fast = new LifecycleService();
        var slow = new BlockingRoutingService();
        var locator = new PathLocator(Map.of("fast", fast, "slow", slow));
        var route = locatorRoute(locator);

        locate(route, "/fast/1");

        CompletableFuture<Void> slowCreation = CompletableFuture.runAsync(() -> locate(route, "/slow/1"));
        if (!slow.awaitStarted()) {
            slow.release();
            fail("Cold service route creation did not start");
        }

        CompletableFuture<Void> fastHit = CompletableFuture.runAsync(() -> locate(route, "/fast/2"));
        try {
            fastHit.get(1, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Cached route should not wait for another service identity to finish cold creation", e);
        } finally {
            slow.release();
        }

        slowCreation.get(5, TimeUnit.SECONDS);
        assertThat(fast.routingCount.get(), is(1));
        assertThat(slow.routingCount.get(), is(1));
    }

    private static ServiceLocatorRoute locatorRoute(HttpServiceLocator locator) {
        return new ServiceLocatorRoute(locator, PathMatchers.create("/{item}"), Method.predicate(Method.GET));
    }

    private static void locate(ServiceLocatorRoute route) {
        locate(route, "/pipe/123");
    }

    private static void locate(ServiceLocatorRoute route, String path) {
        RoutingRequest request = mock(RoutingRequest.class);
        AtomicReference<RoutedPath> requestPath = new AtomicReference<>();
        PathMatchers.PrefixMatchResult match = route.acceptsPrefix(prologue(path));

        when(request.path()).thenAnswer(inv -> requestPath.get());
        when(request.path(nullable(RoutedPath.class))).thenAnswer(inv -> {
            requestPath.set(inv.getArgument(0));
            return request;
        });

        route.routes(mock(ConnectionContext.class), request, match.matchedPath(), "/{item}");
    }

    private static HttpPrologue prologue(String path) {
        return HttpPrologue.create("http/1.1",
                                   "http",
                                   "1.1",
                                   Method.GET,
                                   UriPath.create(path),
                                   UriQuery.empty(),
                                   UriFragment.empty());
    }

    private static boolean containsMessage(Throwable failure, String message) {
        if (failure.getMessage() != null && failure.getMessage().contains(message)) {
            return true;
        }
        for (Throwable suppressed : failure.getSuppressed()) {
            if (containsMessage(suppressed, message)) {
                return true;
            }
        }
        Throwable cause = failure.getCause();
        return cause != null && containsMessage(cause, message);
    }

    private static class ItemService implements HttpService {
        private final String name;

        ItemService(String name) {
            this.name = name;
        }

        @Override
        public void routing(HttpRules rules) {
            rules.get("/{id}", (req, res) -> res.send(name
                    + ":" + req.path().pathParameters().first("item").get()
                    + ":" + req.path().pathParameters().first("id").get()
                    + ":" + req.matchingPattern().orElse("")));
        }
    }

    private static class LifecycleLocator implements HttpServiceLocator {
        private final HttpService service;
        private final AtomicInteger locateCount = new AtomicInteger();
        private final AtomicInteger beforeStartCount = new AtomicInteger();
        private final AtomicInteger afterStartCount = new AtomicInteger();
        private final AtomicInteger afterStopCount = new AtomicInteger();

        LifecycleLocator(HttpService service) {
            this.service = service;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            locateCount.incrementAndGet();
            return Optional.of(service);
        }

        @Override
        public void beforeStart() {
            beforeStartCount.incrementAndGet();
        }

        @Override
        public void afterStart(WebServer webServer) {
            afterStartCount.incrementAndGet();
        }

        @Override
        public void afterStop() {
            afterStopCount.incrementAndGet();
        }
    }

    private static class SwitchingLocator implements HttpServiceLocator {
        private final boolean failAfterStop;
        private final int maxServiceCacheSize;
        private HttpService service;

        SwitchingLocator(HttpService service, boolean failAfterStop) {
            this(service, failAfterStop, DEFAULT_MAX_SERVICE_CACHE_SIZE);
        }

        SwitchingLocator(HttpService service, boolean failAfterStop, int maxServiceCacheSize) {
            this.service = service;
            this.failAfterStop = failAfterStop;
            this.maxServiceCacheSize = maxServiceCacheSize;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return Optional.of(service);
        }

        @Override
        public int maxServiceCacheSize() {
            return maxServiceCacheSize;
        }

        @Override
        public void afterStop() {
            if (failAfterStop) {
                throw new IllegalStateException("afterStop failed locator");
            }
        }

        void service(HttpService service) {
            this.service = service;
        }
    }

    private static class PathLocator implements HttpServiceLocator {
        private final Map<String, HttpService> services;

        PathLocator(Map<String, HttpService> services) {
            this.services = services;
        }

        @Override
        public Optional<HttpService> locate(ServerRequest request) {
            return request.path()
                    .pathParameters()
                    .first("item")
                    .map(services::get);
        }
    }

    private static class LifecycleService implements HttpService {
        private final String name;
        private final boolean failBeforeStart;
        private final boolean failAfterStart;
        private final boolean failAfterStop;
        private final AtomicInteger routingCount = new AtomicInteger();
        private final AtomicInteger beforeStartCount = new AtomicInteger();
        private final AtomicInteger afterStartCount = new AtomicInteger();
        private final AtomicInteger afterStopCount = new AtomicInteger();

        LifecycleService() {
            this("service");
        }

        LifecycleService(String name) {
            this(name, false, false);
        }

        LifecycleService(String name, boolean failAfterStart, boolean failAfterStop) {
            this(name, false, failAfterStart, failAfterStop);
        }

        LifecycleService(String name, boolean failBeforeStart, boolean failAfterStart, boolean failAfterStop) {
            this.name = name;
            this.failBeforeStart = failBeforeStart;
            this.failAfterStart = failAfterStart;
            this.failAfterStop = failAfterStop;
        }

        @Override
        public void routing(HttpRules rules) {
            routingCount.incrementAndGet();
            rules.get("/{id}", (req, res) -> res.send("service"));
        }

        @Override
        public void beforeStart() {
            beforeStartCount.incrementAndGet();
            if (failBeforeStart) {
                throw new IllegalStateException("beforeStart failed " + name);
            }
        }

        @Override
        public void afterStart(WebServer webServer) {
            afterStartCount.incrementAndGet();
            if (failAfterStart) {
                throw new IllegalStateException("afterStart failed " + name);
            }
        }

        @Override
        public void afterStop() {
            afterStopCount.incrementAndGet();
            if (failAfterStop) {
                throw new IllegalStateException("afterStop failed " + name);
            }
        }
    }

    private static class BlockingRoutingService implements HttpService {
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger routingCount = new AtomicInteger();

        @Override
        public void routing(HttpRules rules) {
            routingCount.incrementAndGet();
            started.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            rules.get("/{id}", (req, res) -> res.send("slow"));
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(5, TimeUnit.SECONDS);
        }

        private void release() {
            release.countDown();
        }
    }

    private static final class RoutingInvocation {
        private final ConnectionContext ctx = mock(ConnectionContext.class);
        private final RoutingRequest request = mock(RoutingRequest.class);
        private final RoutingResponse response = mock(RoutingResponse.class);
        private final ListenerContext listenerContext = mock(ListenerContext.class);
        private final AtomicReference<HttpPrologue> prologue;
        private final AtomicReference<RoutedPath> path = new AtomicReference<>();
        private final AtomicReference<String> matchingPattern = new AtomicReference<>();
        private final AtomicReference<Object> entity = new AtomicReference<>();
        private final AtomicBoolean nexted = new AtomicBoolean();
        private final AtomicBoolean rerouted = new AtomicBoolean();
        private final AtomicReference<String> reroutePath = new AtomicReference<>();

        private RoutingInvocation(String path) {
            this.prologue = new AtomicReference<>(prologue(path));
            when(ctx.listenerContext()).thenReturn(listenerContext);
            when(listenerContext.directHandlers()).thenReturn(DirectHandlers.create());
            when(request.prologue()).thenAnswer(inv -> prologue.get());
            when(request.prologue(any(HttpPrologue.class))).thenAnswer(inv -> {
                prologue.set(inv.getArgument(0));
                return request;
            });
            when(request.headers()).thenReturn(ServerRequestHeaders.create());
            when(request.content()).thenReturn(ReadableEntityBase.empty());
            when(request.path()).thenAnswer(inv -> this.path.get());
            when(request.path(nullable(RoutedPath.class))).thenAnswer(inv -> {
                this.path.set(inv.getArgument(0));
                return request;
            });
            when(request.matchingPattern()).thenAnswer(inv -> Optional.ofNullable(matchingPattern.get()));
            when(request.matchingPattern(anyString())).thenAnswer(inv -> {
                matchingPattern.set(inv.getArgument(0));
                return request;
            });

            doAnswer(inv -> {
                nexted.set(false);
                rerouted.set(false);
                return null;
            }).when(response).resetRouting();
            when(response.next()).thenAnswer(inv -> {
                nexted.set(true);
                return response;
            });
            when(response.reroute(anyString())).thenAnswer(inv -> {
                reroutePath.set(inv.getArgument(0));
                rerouted.set(true);
                return response;
            });
            when(response.reroutePrologue(any(HttpPrologue.class))).thenAnswer(inv -> prologue(reroutePath.get()));
            when(response.shouldReroute()).thenAnswer(inv -> rerouted.get());
            when(response.isNexted()).thenAnswer(inv -> nexted.get());
            when(response.hasEntity()).thenAnswer(inv -> entity.get() != null);
            when(response.isSent()).thenAnswer(inv -> entity.get() != null);
            when(response.reset()).thenReturn(true);
            when(response.resetStream()).thenReturn(true);
            when(response.status()).thenReturn(Status.OK_200);
            doAnswer(inv -> {
                entity.set("");
                return null;
            }).when(response).send();
            doAnswer(inv -> {
                entity.set(inv.getArgument(0));
                return null;
            }).when(response).send(any(Object.class));
            doAnswer(inv -> {
                entity.set(inv.getArgument(0));
                return null;
            }).when(response).send(any(byte[].class));
        }

        static RoutingInvocation create(String path) {
            return new RoutingInvocation(path);
        }

        void route(HttpRouting routing) throws Exception {
            routing.route(ctx, request, response);
        }

        Object entity() {
            return entity.get();
        }

        RoutedPath path() {
            return path.get();
        }

        private static HttpPrologue prologue(String path) {
            return HttpServiceLocatorTest.prologue(path);
        }
    }
}
