/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import io.helidon.common.http.Http;

/**
 * A {@link Routing.Rules} implementation collecting all routings into single {@link RouteList}.
 */
class RouteListRoutingRules implements Routing.Rules {

    private final List<Record> records = new ArrayList<>();
    private final List<Consumer<WebServer>> newWebServerCallbacks = new ArrayList<>();
    private final List<Service> contextServices;

    private RouteListRoutingRules(Collection<Service> parentContexts, Service service) {
        if (parentContexts == null || parentContexts.isEmpty()) {
            this.contextServices = service == null ? Collections.emptyList() : Collections.singletonList(service);
        } else {
            this.contextServices = new ArrayList<>(parentContexts.size() + 1);
            this.contextServices.addAll(parentContexts);
            if (service != null) {
                this.contextServices.add(service);
            }
        }
    }

    RouteListRoutingRules() {
        this(null, null);
    }

    /**
     * Returns a copy of current state. Registered sub-routings are resolved by the method call.
     *
     * @param pathContext an URI path context.
     * @return a copy of current state.
     */
    Aggregation aggregate(PathMatcher pathContext) {
        List<Route> result = new ArrayList<>();
        List<Consumer<WebServer>> resultCallbacks = new ArrayList<>(newWebServerCallbacks);
        for (Record record : records) {
            if (record.route != null) {
                result.add(record.route);
            } else if (record.services != null) {
                // Apply all services
                List<Aggregation> subAggregations = new ArrayList<>();
                for (Service service : record.services) {
                    RouteListRoutingRules rules = new RouteListRoutingRules(this.contextServices, service);
                    service.update(rules);
                    // Use only non-empty
                    if (!rules.isEmpty()) {
                        Aggregation aggr = rules.aggregate(record.pathContext);
                        if (!aggr.isEmpty()) {
                            subAggregations.add(aggr);
                        }
                    }
                }
                // Collect aggregations
                if (!subAggregations.isEmpty()) {
                    Aggregation subAggregation = Aggregation.concatWithSamePath(subAggregations);
                    // Insert into current result
                    resultCallbacks.addAll(subAggregation.newWebServerCallbacks());
                    if (!subAggregation.routeList().isEmpty()) {
                        if (record.pathContext == null) {
                            // Can flat it
                            result.addAll(subAggregation.routeList());
                        } else {
                            result.add(subAggregation.routeList());
                        }
                    }
                }
            }
        }
        return new Aggregation(new RouteList(pathContext, result), resultCallbacks);
    }

    /**
     * Returns a copy of current state. Registered sub-routings are resolved by the method call.
     *
     * @return a copy of current state.
     */
    Aggregation aggregate() {
        return aggregate(null);
    }

    private boolean isEmpty() {
        return records.isEmpty() && newWebServerCallbacks.isEmpty() && contextServices.isEmpty();
    }

    @Override
    public RouteListRoutingRules register(WebTracingConfig webTracingConfig) {
        onNewWebServer(ws -> ws.context().register(webTracingConfig.envConfig()));

        Service[] services = {webTracingConfig.service()};
        Record record = new Record(null, services);

        // need tracing service to be the very first one, as it must start the span before other handlers are invoked
        records.add(0, record);

        return this;
    }

    @Override
    public RouteListRoutingRules onNewWebServer(Consumer<WebServer> webServerConsumer) {
        if (webServerConsumer != null) {
            newWebServerCallbacks.add(webServerConsumer);
        }
        return this;
    }

    @Override
    public RouteListRoutingRules register(Service... services) {
        if (services != null && services.length > 0) {
            records.add(new Record(null, services));
        }
        return this;
    }

    @Override
    public RouteListRoutingRules register(Supplier<? extends Service>... serviceBuilders) {
        if (serviceBuilders != null && serviceBuilders.length > 0) {
            records.add(new Record(null,
                                   Stream.of(serviceBuilders)
                                         .filter(Objects::nonNull)
                                         .map(Supplier::get)
                                         .toArray(Service[]::new)));
        }
        return this;
    }

    @Override
    public RouteListRoutingRules register(String pathPattern, Service... services) {
        if (services != null && services.length > 0) {
            records.add(new Record(PathPattern.compile(pathPattern), services));
        }
        return this;
    }

    @Override
    public RouteListRoutingRules register(String pathPattern, Supplier<? extends Service>... serviceBuilders) {
        if (serviceBuilders != null && serviceBuilders.length > 0) {
            records.add(new Record(PathPattern.compile(pathPattern),
                                   Stream.of(serviceBuilders)
                                         .filter(Objects::nonNull)
                                         .map(Supplier::get)
                                         .toArray(Service[]::new)));
        }
        return this;
    }

    private RouteListRoutingRules addSingle(Http.RequestMethod method, PathMatcher pathMatcher, Handler... requestHandlers) {
        if (requestHandlers != null) {
            for (Handler requestHandler : requestHandlers) {
                if (pathMatcher == null) {
                    records.add(new Record(new HandlerRoute(contextServices, requestHandler, method)));
                } else {
                    records.add(new Record(new HandlerRoute(contextServices, pathMatcher, requestHandler, method)));
                }
            }
        }
        return this;
    }

    private RouteListRoutingRules addSingle(Http.RequestMethod method, String pathPattern, Handler... requestHandlers) {
        if (pathPattern != null) {
            return addSingle(method, PathPattern.compile(pathPattern), requestHandlers);
        } else {
            return addSingle(method, (PathMatcher) null, requestHandlers);
        }
    }

    private RouteListRoutingRules addSingle(Http.RequestMethod method, Handler... requestHandlers) {
        return addSingle(method, (PathMatcher) null, requestHandlers);

    }

    @Override
    public RouteListRoutingRules get(Handler... requestHandlers) {
        return addSingle(Http.Method.GET, requestHandlers);
    }

    @Override
    public RouteListRoutingRules get(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.GET, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules get(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.GET, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules put(Handler... requestHandlers) {
        return addSingle(Http.Method.PUT, requestHandlers);
    }

    @Override
    public RouteListRoutingRules put(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.PUT, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules put(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.PUT, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules post(Handler... requestHandlers) {
        return addSingle(Http.Method.POST, requestHandlers);
    }

    @Override
    public RouteListRoutingRules post(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.POST, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules post(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.POST, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules patch(Handler... requestHandlers) {
        return addSingle(Http.Method.PATCH, requestHandlers);
    }

    @Override
    public RouteListRoutingRules patch(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.PATCH, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules patch(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.PATCH, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules delete(Handler... requestHandlers) {
        return addSingle(Http.Method.DELETE, requestHandlers);
    }

    @Override
    public RouteListRoutingRules delete(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.DELETE, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules delete(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.DELETE, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules options(Handler... requestHandlers) {
        return addSingle(Http.Method.OPTIONS, requestHandlers);
    }

    @Override
    public RouteListRoutingRules options(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.OPTIONS, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules options(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.OPTIONS, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules head(Handler... requestHandlers) {
        return addSingle(Http.Method.HEAD, requestHandlers);
    }

    @Override
    public RouteListRoutingRules head(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.HEAD, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules head(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.HEAD, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules trace(Handler... requestHandlers) {
        return addSingle(Http.Method.TRACE, requestHandlers);
    }

    @Override
    public RouteListRoutingRules trace(String pathPattern, Handler... requestHandlers) {
        return addSingle(Http.Method.TRACE, pathPattern, requestHandlers);
    }

    @Override
    public RouteListRoutingRules trace(PathMatcher pathMatcher, Handler... requestHandlers) {
        return addSingle(Http.Method.TRACE, pathMatcher, requestHandlers);
    }

    @Override
    public RouteListRoutingRules any(Handler... requestHandlers) {
        return any((PathMatcher) null, requestHandlers);
    }

    @Override
    public RouteListRoutingRules any(String pathPattern, Handler... requestHandlers) {
        if (pathPattern == null) {
            return any((PathMatcher) null, requestHandlers);
        } else {
            return any(PathMatcher.create(pathPattern), requestHandlers);
        }
    }

    @Override
    public RouteListRoutingRules any(PathMatcher pathMatcher, Handler... requestHandlers) {
        if (requestHandlers != null) {
            for (Handler requestHandler : requestHandlers) {
                if (pathMatcher == null) {
                    records.add(new Record(new HandlerRoute(contextServices, requestHandler)));
                } else {
                    records.add(new Record(new HandlerRoute(contextServices, pathMatcher, requestHandler)));
                }
            }
        }
        return this;
    }

    @Override
    public RouteListRoutingRules anyOf(Iterable<Http.RequestMethod> methods, Handler... requestHandlers) {
        return anyOf(methods, (PathMatcher) null, requestHandlers);
    }

    @Override
    public RouteListRoutingRules anyOf(Iterable<Http.RequestMethod> methods, String pathPattern, Handler... requestHandlers) {
        if (pathPattern == null) {
            return anyOf(methods, (PathMatcher) null, requestHandlers);
        } else {
            return anyOf(methods, PathMatcher.create(pathPattern), requestHandlers);
        }
    }

    @Override
    public RouteListRoutingRules anyOf(
            Iterable<Http.RequestMethod> methods, PathMatcher pathMatcher, Handler... requestHandlers) {
        if (requestHandlers != null) {
            for (Handler requestHandler : requestHandlers) {
                if (pathMatcher == null) {
                    records.add(new Record(new HandlerRoute(contextServices, requestHandler, methods)));
                } else {
                    records.add(new Record(new HandlerRoute(contextServices, pathMatcher, requestHandler, methods)));
                }
            }
        }
        return this;
    }

    /**
     * Represents a record for the internal state.
     */
    private static class Record {

        private final Route route;
        private final PathMatcher pathContext;
        private final Service[] services;

        Record(Route route) {
            this.route = route;
            this.pathContext = null;
            this.services = null;
        }

        Record(PathMatcher pathContext, Service[] services) {
            this.route = null;
            this.pathContext = pathContext;
            this.services = services;
        }
    }

    /**
     * Aggregated result.
     */
    static final class Aggregation {
        private final RouteList routeList;
        private final List<Consumer<WebServer>> newWebServerCallbacks;

        private Aggregation(RouteList routeList,
                           List<Consumer<WebServer>> newWebServerCallbacks) {
            this.routeList = routeList;
            this.newWebServerCallbacks = newWebServerCallbacks;
        }

        RouteList routeList() {
            return routeList;
        }

        boolean isEmpty() {
            return routeList.isEmpty() && newWebServerCallbacks.isEmpty();
        }

        List<Consumer<WebServer>> newWebServerCallbacks() {
            return newWebServerCallbacks;
        }

        /**
         * Concats several aggregation with the same path matcher.
         */
        private static Aggregation concatWithSamePath(List<Aggregation> aggregations) {
            if (aggregations == null || aggregations.isEmpty()) {
                throw new IllegalArgumentException("Parameter aggregations is null or empty!");
            }
            if (aggregations.size() == 1) {
                return aggregations.get(0);
            } else {
                List<Consumer<WebServer>> callbacks = new ArrayList<>();
                Collection<Route> routes = new ArrayList<>();
                for (Aggregation aggregation : aggregations) {
                    callbacks.addAll(aggregation.newWebServerCallbacks);
                    routes.addAll(aggregation.routeList);
                }
                return new Aggregation(new RouteList(aggregations.get(0).routeList.pathContext(), routes), callbacks);
            }
        }
    }
}
