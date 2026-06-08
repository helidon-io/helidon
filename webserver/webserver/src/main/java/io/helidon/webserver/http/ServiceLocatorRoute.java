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

import io.helidon.http.HttpPrologue;
import io.helidon.http.Method;
import io.helidon.http.PathMatcher;
import io.helidon.http.PathMatchers;
import io.helidon.http.RoutedPath;
import io.helidon.webserver.ConnectionContext;
import io.helidon.webserver.WebServer;

class ServiceLocatorRoute extends HttpRouteBase implements HttpRoute {
    private final HttpServiceLocator locator;
    private final Predicate<Method> methodPredicate;
    private final PathMatcher pathMatcher;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<HttpService, RouteEntry> routes = new IdentityHashMap<>();

    private boolean beforeStarted;
    private boolean stopped;
    private WebServer webServer;

    ServiceLocatorRoute(HttpServiceLocator locator,
                        PathMatcher pathMatcher,
                        Predicate<Method> methodPredicate) {
        this.locator = Objects.requireNonNull(locator);
        this.methodPredicate = methodPredicate;
        this.pathMatcher = pathMatcher;
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
                ServiceRoute route = entry.route();
                if (route != null) {
                    failure = run(failure, route::afterStop);
                }
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
        request.path(matchedPath);

        Optional<HttpService> locatedService = Objects.requireNonNull(locator.locate(request),
                                                                      "HttpServiceLocator must not return null");
        return locatedService.map(this::route)
                .map(ServiceRoute::routes)
                .orElseGet(List::of);
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

        RouteEntry entry = routeEntry(service);
        if (entry == null) {
            return null;
        }

        try {
            lock.readLock().lock();
            try {
                if (stopped) {
                    return null;
                }
                return entry.route(this, service);
            } finally {
                lock.readLock().unlock();
            }
        } catch (RuntimeException | Error e) {
            removeEntryIfEmpty(service, entry);
            throw e;
        }
    }

    private RouteEntry routeEntry(HttpService service) {
        lock.readLock().lock();
        try {
            if (stopped) {
                return null;
            }
            RouteEntry entry = routes.get(service);
            if (entry != null) {
                return entry;
            }
        } finally {
            lock.readLock().unlock();
        }

        lock.writeLock().lock();
        try {
            if (stopped) {
                return null;
            }
            return routes.computeIfAbsent(service, _ -> new RouteEntry());
        } finally {
            lock.writeLock().unlock();
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
        ServiceRoute route = subRules.build();

        boolean lifecycleStartAttempted = false;
        try {
            if (beforeStarted) {
                lifecycleStartAttempted = true;
                route.beforeStart();
            }
            if (webServer != null) {
                lifecycleStartAttempted = true;
                route.afterStart(webServer);
            }
        } catch (RuntimeException | Error e) {
            if (lifecycleStartAttempted) {
                run(e, route::afterStop);
            }
            throw e;
        }

        return route;
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

        private ServiceRoute route(ServiceLocatorRoute owner, HttpService service) {
            ServiceRoute current = route;
            if (current != null) {
                return current;
            }

            lock.lock();
            try {
                if (route == null) {
                    route = owner.createRoute(service);
                }
                return route;
            } finally {
                lock.unlock();
            }
        }

        private ServiceRoute route() {
            return route;
        }

        private void ifCreated(Consumer<ServiceRoute> consumer) {
            ServiceRoute current = route;
            if (current != null) {
                consumer.accept(current);
            }
        }
    }
}
