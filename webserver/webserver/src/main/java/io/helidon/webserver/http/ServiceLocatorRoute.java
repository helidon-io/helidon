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
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
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
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<HttpService, RouteEntry> routes = new IdentityHashMap<>();
    private final int maxServiceCacheSize;

    private volatile boolean beforeStarted;
    private volatile boolean stopped;
    private volatile WebServer webServer;

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
        lock.writeLock().lock();
        try {
            stopped = false;
            beforeStarted = true;
            locator.beforeStart();
            routes.values().forEach(entry -> entry.ifCreated(ServiceRoute::beforeStart));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterStart(WebServer webServer) {
        lock.writeLock().lock();
        try {
            this.webServer = webServer;
            locator.afterStart(webServer);
            routes.values().forEach(entry -> entry.ifCreated(route -> route.afterStart(webServer)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void afterStop() {
        lock.writeLock().lock();
        try {
            stopped = true;
            webServer = null;
            Throwable failure = run(null, locator::afterStop);
            for (RouteEntry entry : routes.values()) {
                failure = entry.afterStop(failure);
            }
            routes.clear();
            throwIfFailed(failure);
        } finally {
            lock.writeLock().unlock();
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
        Optional<String> previousMatchingPattern = request.matchingPattern();
        request.path(matchedPath);
        request.matchingPattern(matchingPattern);

        try {
            Optional<HttpService> locatedService = Objects.requireNonNull(locator.locate(request),
                                                                          "HttpServiceLocator must not return null");
            return locatedService.map(this::route)
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

    private ServiceRoute route(HttpService service) {
        Objects.requireNonNull(service, "HttpServiceLocator must not locate a null service");

        RouteEntry entry = null;
        try {
            lock.readLock().lock();
            try {
                if (stopped) {
                    return null;
                }
                entry = routes.get(service);
            } finally {
                lock.readLock().unlock();
            }

            if (entry == null) {
                lock.writeLock().lock();
                try {
                    if (stopped) {
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
                        routes.put(service, entry);
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }

            ServiceRoute route = entry.route(this, service);
            if (route == null) {
                if (!stopped) {
                    removeEntryIfEmpty(service, entry);
                }
                return null;
            }
            return stopped ? null : route;
        } catch (RuntimeException | Error e) {
            if (entry != null) {
                removeEntryIfEmpty(service, entry);
            }
            throw e;
        }
    }

    private void removeEntryIfEmpty(HttpService service, RouteEntry entry) {
        lock.writeLock().lock();
        try {
            if (entry.route() == null && routes.get(service) == entry) {
                routes.remove(service);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private ServiceRoute createRoute(HttpService service) {
        ServiceRules subRules = new ServiceRules(service, PathMatchers.any(), methodPredicate);
        service.routing(subRules);
        return subRules.build();
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
        private volatile ServiceRoute route;
        private volatile boolean initialized;

        private ServiceRoute route(ServiceLocatorRoute owner, HttpService service) {
            ServiceRoute current = route;
            if (initialized && current != null) {
                return current;
            }

            lock.lock();
            try {
                if (initialized) {
                    return route;
                }

                current = owner.createRoute(service);
                if (owner.stopped) {
                    return null;
                }

                route = current;
                boolean lifecycleStartAttempted = false;
                try {
                    if (!owner.stopped && owner.beforeStarted) {
                        lifecycleStartAttempted = true;
                        current.beforeStart();
                    }
                    WebServer webServer = owner.webServer;
                    if (!owner.stopped && webServer != null) {
                        lifecycleStartAttempted = true;
                        current.afterStart(webServer);
                    }
                    if (route != null) {
                        initialized = true;
                    }
                    return route;
                } catch (RuntimeException | Error e) {
                    route = null;
                    initialized = false;
                    if (lifecycleStartAttempted) {
                        run(e, current::afterStop);
                    }
                    throw e;
                }
            } finally {
                lock.unlock();
            }
        }

        private ServiceRoute route() {
            return route;
        }

        private void ifCreated(Consumer<ServiceRoute> consumer) {
            lock.lock();
            try {
                ServiceRoute current = route;
                if (initialized && current != null) {
                    consumer.accept(current);
                }
            } finally {
                lock.unlock();
            }
        }

        private Throwable afterStop(Throwable failure) {
            lock.lock();
            try {
                ServiceRoute current = route;
                route = null;
                initialized = false;
                return current == null ? failure : run(failure, current::afterStop);
            } finally {
                lock.unlock();
            }
        }
    }
}
