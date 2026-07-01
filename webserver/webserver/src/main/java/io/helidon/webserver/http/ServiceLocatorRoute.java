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

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import io.helidon.http.HttpException;
import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.RoutedPath;
import io.helidon.http.Status;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.WebServer;

class ServiceLocatorRoute extends HttpRouteBase implements HttpRoute {
    private final HttpServiceLocator locator;
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxServiceCacheSize;

    // Published maps are not mutated. Cache changes copy the identity map while holding the lock.
    private volatile Map<HttpService, RouteEntry> routes = new IdentityHashMap<>();
    private volatile Lifecycle lifecycle = Lifecycle.initial();

    ServiceLocatorRoute(HttpServiceLocator locator,
                        PathMatcher pathMatcher,
                        Predicate<Method> methodPredicate) {
        this.locator = Objects.requireNonNull(locator);
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
        this.maxServiceCacheSize = locator.maxServiceCacheSize();
        if (maxServiceCacheSize < 1) {
            throw new IllegalArgumentException("HttpServiceLocator maxServiceCacheSize must be greater than zero");
        }
    }

    @Override
    public void beforeStart() {
        Lifecycle transition;
        lock.lock();
        try {
            transition = lifecycle.beforeStarting();
            lifecycle = transition;
        } finally {
            lock.unlock();
        }

        locator.beforeStart();
        completeStartTransition(transition, transition.beforeStarted());
    }

    @Override
    public void afterStart(WebServer webServer) {
        Lifecycle transition;
        lock.lock();
        try {
            transition = lifecycle.afterStarting(webServer);
            lifecycle = transition;
        } finally {
            lock.unlock();
        }

        locator.afterStart(webServer);
        completeStartTransition(transition, transition.started(webServer));
    }

    @Override
    public void afterStop() {
        Lifecycle transition;
        List<RouteEntry> entries;
        lock.lock();
        try {
            transition = lifecycle.stopping();
            lifecycle = transition;
            entries = List.copyOf(routes.values());
            routes = new IdentityHashMap<>();
        } finally {
            lock.unlock();
        }

        Throwable failure = run(null, locator::afterStop);
        for (RouteEntry entry : entries) {
            failure = entry.afterStop(failure);
        }

        lock.lock();
        try {
            if (lifecycle == transition) {
                lifecycle = transition.stopped();
            }
        } finally {
            lock.unlock();
        }
        throwIfFailed(failure);
    }

    private void completeStartTransition(Lifecycle transition, Lifecycle completed) {
        List<RouteEntry> entries;
        lock.lock();
        try {
            if (lifecycle != transition) {
                return;
            }
            lifecycle = completed;
            entries = List.copyOf(routes.values());
        } finally {
            lock.unlock();
        }

        for (RouteEntry entry : entries) {
            entry.requestCatchUp(this);
        }
    }

    @Override
    public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
        throw new IllegalStateException("List routes must use acceptsPrefix");
    }

    @Override
    public Handler handler() {
        throw new IllegalStateException("Service locator routing cannot provide a handler, use list of routes");
    }

    @Override
    PathMatchers.PrefixMatchResult acceptsPrefix(HttpPrologue prologue) {
        if (!methodPredicate.test(prologue.method())) {
            return PathMatchers.PrefixMatchResult.notAccepted();
        }

        return pathMatcher.prefixMatch(prologue.uriPath());
    }

    @Override
    List<HttpRouteBase> routes(ConnectionContext ctx,
                               RoutingRequest request,
                               RoutedPath matchedPath,
                               String matchingPattern) {
        return routes(ctx, request, matchedPath, matchingPattern, false);
    }

    HttpRouteBase protocolUpgradeRoute() {
        return new ProtocolUpgradeRoute(this);
    }

    private List<HttpRouteBase> routes(ConnectionContext ctx,
                                       RoutingRequest request,
                                       RoutedPath matchedPath,
                                       String matchingPattern,
                                       boolean protocolUpgrade) {
        Optional<String> previousMatchingPattern = request.matchingPattern();
        request.path(matchedPath);
        request.matchingPattern(matchingPattern);

        try {
            Optional<HttpService> locatedService = Objects.requireNonNull(locator.locate(request),
                                                                          "HttpServiceLocator must not return null");
            return locatedService.map(this::route)
                    .map(routes -> protocolUpgrade ? routes.protocolUpgrade() : routes.routing())
                    .map(ServiceRoute::routes)
                    .orElseGet(List::of);
        } finally {
            request.matchingPattern(previousMatchingPattern.orElse(null));
        }
    }

    @Override
    List<HttpRouteBase> routes() {
        throw new IllegalStateException("Service locator routes must be located for a request");
    }

    @Override
    void afterNoMatch(RoutingRequest request, RoutedPath previousPath) {
        request.path(previousPath);
    }

    @Override
    boolean requestAwareRoutes() {
        return true;
    }

    @Override
    boolean isList() {
        return true;
    }

    @Override
    public Optional<PathMatcher> pathMatcher() {
        return Optional.of(pathMatcher);
    }

    @Override
    public String toString() {
        return methodPredicate + " (" + pathMatcher + ") with service locator: " + locator;
    }

    private LocatedRoutes route(HttpService service) {
        Objects.requireNonNull(service, "HttpServiceLocator must not locate a null service");

        if (!lifecycle.acceptsRoutes()) {
            return null;
        }
        RouteEntry entry = routes.get(service);

        if (entry == null) {
            lock.lock();
            try {
                if (!lifecycle.acceptsRoutes()) {
                    return null;
                }
                entry = routes.get(service);
                if (entry == null) {
                    if (routes.size() >= maxServiceCacheSize) {
                        throw new HttpException("HttpServiceLocator service cache size of "
                                                        + maxServiceCacheSize
                                                        + " exceeded",
                                                Status.SERVICE_UNAVAILABLE_503,
                                                true);
                    }
                    entry = new RouteEntry();
                    Map<HttpService, RouteEntry> updatedRoutes = new IdentityHashMap<>(routes);
                    updatedRoutes.put(service, entry);
                    routes = updatedRoutes;
                }
            } finally {
                lock.unlock();
            }
        }

        try {
            LocatedRoutes locatedRoutes = entry.routes(this, service);
            return lifecycle.acceptsRoutes() ? locatedRoutes : null;
        } catch (RuntimeException | Error e) {
            entry.removeIfEmpty(this, service);
            throw e;
        }
    }

    private LocatedRoutes createRoutes(HttpService service) {
        ServiceRules subRules = new ServiceRules(service, PathMatchers.any(), methodPredicate);
        service.routing(subRules);
        return new LocatedRoutes(subRules.build(), subRules.buildProtocolUpgrade());
    }

    private static Throwable run(Throwable failure, Runnable runnable) {
        try {
            runnable.run();
            return failure;
        } catch (RuntimeException | Error e) {
            if (failure == null || failure == e) {
                return e;
            }
            failure.addSuppressed(e);
            return failure;
        }
    }

    private static void throwIfFailed(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw (RuntimeException) failure;
    }

    private static final class RouteEntry {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicBoolean catchUpRequested = new AtomicBoolean();
        private volatile LocatedRoutes routes;
        private volatile boolean initialized;
        private volatile LifecycleStamp lifecycleStamp = LifecycleStamp.initial();

        private LocatedRoutes routes(ServiceLocatorRoute owner, HttpService service) {
            LocatedRoutes current = routes;
            Lifecycle target = owner.lifecycle;
            if (initialized && current != null && lifecycleStamp.covers(target) && !lock.isLocked()) {
                return current;
            }

            lock.lock();
            try {
                if (!initialized) {
                    owner.lock.lock();
                    try {
                        if (!owner.lifecycle.acceptsRoutes() || owner.routes.get(service) != this) {
                            return null;
                        }
                    } finally {
                        owner.lock.unlock();
                    }
                    current = owner.createRoutes(service);
                    routes = current;
                    initialized = true;
                    lifecycleStamp = LifecycleStamp.initial(target.generation());
                }

                catchUp(owner);
            } finally {
                lock.unlock();
            }
            catchUpIfRequested(owner);
            return owner.lifecycle.acceptsRoutes() ? routes : null;
        }

        private void removeIfEmpty(ServiceLocatorRoute owner, HttpService service) {
            lock.lock();
            try {
                if (routes != null) {
                    return;
                }

                owner.lock.lock();
                try {
                    if (owner.routes.get(service) == this) {
                        Map<HttpService, RouteEntry> updatedRoutes = new IdentityHashMap<>(owner.routes);
                        updatedRoutes.remove(service);
                        owner.routes = updatedRoutes;
                    }
                } finally {
                    owner.lock.unlock();
                }
            } finally {
                lock.unlock();
            }
        }

        private void requestCatchUp(ServiceLocatorRoute owner) {
            catchUpRequested.set(true);
            catchUpIfRequested(owner);
        }

        private void catchUpIfRequested(ServiceLocatorRoute owner) {
            while (catchUpRequested.get() && lock.tryLock()) {
                try {
                    if (initialized && routes != null) {
                        catchUp(owner);
                    } else {
                        catchUpRequested.set(false);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        private void catchUp(ServiceLocatorRoute owner) {
            while (true) {
                catchUpRequested.set(false);
                Lifecycle target = owner.lifecycle;
                advance(target);
                if (!catchUpRequested.get() && owner.lifecycle == target) {
                    return;
                }
            }
        }

        private void advance(Lifecycle target) {
            LocatedRoutes current = routes;
            if (!initialized || current == null) {
                return;
            }
            if (!target.acceptsRoutes()) {
                return;
            }

            LifecycleStamp currentStamp = lifecycleStamp;
            if (currentStamp.generation() != target.generation()) {
                currentStamp = LifecycleStamp.initial(target.generation());
                lifecycleStamp = currentStamp;
            }

            int requiredPhase = target.requiredPhase();
            if (requiredPhase >= LifecycleStamp.BEFORE_STARTED && currentStamp.phase() < LifecycleStamp.BEFORE_STARTED) {
                lifecycleStamp = new LifecycleStamp(target.generation(), LifecycleStamp.BEFORE_STARTED);
                try {
                    current.routing().beforeStart();
                } catch (RuntimeException | Error e) {
                    clear(e);
                    throw e;
                }
                if (routes != current) {
                    return;
                }
            }

            currentStamp = lifecycleStamp;
            if (requiredPhase >= LifecycleStamp.STARTED && currentStamp.phase() < LifecycleStamp.STARTED) {
                lifecycleStamp = new LifecycleStamp(target.generation(), LifecycleStamp.STARTED);
                try {
                    current.routing().afterStart(target.webServer());
                } catch (RuntimeException | Error e) {
                    clear(e);
                    throw e;
                }
            }
        }

        private void clear(Throwable failure) {
            LocatedRoutes current = routes;
            routes = null;
            initialized = false;
            lifecycleStamp = LifecycleStamp.initial();
            if (current != null) {
                throwIfFailed(run(failure, current.routing()::afterStop));
            }
        }

        private Throwable afterStop(Throwable failure) {
            lock.lock();
            try {
                LocatedRoutes current = routes;
                routes = null;
                initialized = false;
                lifecycleStamp = LifecycleStamp.initial();
                return current == null ? failure : run(failure, current.routing()::afterStop);
            } finally {
                lock.unlock();
            }
        }
    }

    private record LifecycleStamp(long generation, int phase) {
        private static final int BEFORE_STARTED = 1;
        private static final int STARTED = 2;

        private static LifecycleStamp initial() {
            return initial(-1);
        }

        private static LifecycleStamp initial(long generation) {
            return new LifecycleStamp(generation, 0);
        }

        private boolean covers(Lifecycle lifecycle) {
            return lifecycle.acceptsRoutes()
                    && generation == lifecycle.generation()
                    && phase >= lifecycle.requiredPhase();
        }
    }

    private record Lifecycle(long generation, LifecyclePhase phase, WebServer webServer) {
        private static Lifecycle initial() {
            return new Lifecycle(0, LifecyclePhase.INITIAL, null);
        }

        private Lifecycle beforeStarting() {
            return new Lifecycle(generation + 1, LifecyclePhase.BEFORE_STARTING, null);
        }

        private Lifecycle beforeStarted() {
            return new Lifecycle(generation, LifecyclePhase.BEFORE_STARTED, null);
        }

        private Lifecycle afterStarting(WebServer webServer) {
            return new Lifecycle(generation, LifecyclePhase.AFTER_STARTING, webServer);
        }

        private Lifecycle started(WebServer webServer) {
            return new Lifecycle(generation, LifecyclePhase.STARTED, webServer);
        }

        private Lifecycle stopping() {
            return new Lifecycle(generation, LifecyclePhase.STOPPING, null);
        }

        private Lifecycle stopped() {
            return new Lifecycle(generation, LifecyclePhase.STOPPED, null);
        }

        private boolean acceptsRoutes() {
            return phase != LifecyclePhase.STOPPING && phase != LifecyclePhase.STOPPED;
        }

        private int requiredPhase() {
            return switch (phase) {
            case INITIAL, BEFORE_STARTING -> 0;
            case BEFORE_STARTED -> LifecycleStamp.BEFORE_STARTED;
            case AFTER_STARTING, STARTED -> webServer == null
                    ? LifecycleStamp.BEFORE_STARTED
                    : LifecycleStamp.STARTED;
            case STOPPING, STOPPED -> throw new IllegalStateException("Stopped lifecycle does not accept routes");
            };
        }
    }

    private enum LifecyclePhase {
        INITIAL,
        BEFORE_STARTING,
        BEFORE_STARTED,
        AFTER_STARTING,
        STARTED,
        STOPPING,
        STOPPED
    }

    private record LocatedRoutes(ServiceRoute routing, ServiceRoute protocolUpgrade) {
    }

    private static final class ProtocolUpgradeRoute extends HttpRouteBase implements HttpRoute {
        private final ServiceLocatorRoute delegate;

        private ProtocolUpgradeRoute(ServiceLocatorRoute delegate) {
            this.delegate = delegate;
        }

        @Override
        public PathMatchers.MatchResult accepts(HttpPrologue prologue) {
            return delegate.accepts(prologue);
        }

        @Override
        public Handler handler() {
            return delegate.handler();
        }

        @Override
        PathMatchers.PrefixMatchResult acceptsPrefix(HttpPrologue prologue) {
            return delegate.acceptsPrefix(prologue);
        }

        @Override
        List<HttpRouteBase> routes(ConnectionContext ctx,
                                   RoutingRequest request,
                                   RoutedPath matchedPath,
                                   String matchingPattern) {
            return delegate.routes(ctx, request, matchedPath, matchingPattern, true);
        }

        @Override
        List<HttpRouteBase> routes() {
            return delegate.routes();
        }

        @Override
        void afterNoMatch(RoutingRequest request, RoutedPath previousPath) {
            delegate.afterNoMatch(request, previousPath);
        }

        @Override
        boolean requestAwareRoutes() {
            return true;
        }

        @Override
        boolean isList() {
            return true;
        }

        @Override
        public Optional<PathMatcher> pathMatcher() {
            return delegate.pathMatcher();
        }

        @Override
        public String toString() {
            return "protocol upgrade policies for " + delegate;
        }
    }
}
