/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.Builder;
import io.helidon.common.LazyValue;
import io.helidon.common.Weighted;
import io.helidon.common.Weights;
import io.helidon.webserver.http.ErrorHandler;
import io.helidon.webserver.http.Filter;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRoute;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.HttpSecurity;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.spi.ServerFeature;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;

class ServerFeatureContextImpl implements ServerFeature.ServerFeatureContext {
    private final WebServerConfig serverConfig;
    private final Map<String, ListenerBuildersImpl> socketToBuilders;
    private final Set<String> configuredSockets;

    private final AtomicReference<Double> weight;

    private ServerFeatureContextImpl(WebServerConfig serverConfig,
                                     Map<String, ListenerBuildersImpl> socketToBuilders,
                                     AtomicReference<Double> weight) {
        this.serverConfig = serverConfig;
        this.socketToBuilders = socketToBuilders;
        this.configuredSockets = socketToBuilders.keySet()
                .stream()
                .filter(Predicate.not(DEFAULT_SOCKET_NAME::equals))
                .collect(Collectors.toSet());
        this.weight = weight;
    }

    static ServerFeatureContextImpl create(WebServerConfig serverConfig) {
        AtomicReference<Double> weight = new AtomicReference<>();

        Map<String, List<Builder<?, ? extends Routing>>> routingMap = serverConfig.namedRoutings();
        Map<String, ListenerConfig> sockets = serverConfig.sockets();
        HttpRouting.Builder httpRouting = new WeightedBuilder(defaultRouting(serverConfig), weight);
        List<Builder<?, ? extends Routing>> routings = serverConfig.routings();

        Map<String, ListenerBuildersImpl> socketToBuilders = new HashMap<>();
        socketToBuilders.put(DEFAULT_SOCKET_NAME,
                             new ListenerBuildersImpl(DEFAULT_SOCKET_NAME, serverConfig, httpRouting, routings, weight));

        // for each socket, gather all routing builders
        sockets.forEach((socketName, listener) -> {
            List<Builder<?, ? extends Routing>> builders = new ArrayList<>(listener.routings());

            List<Builder<?, ? extends Routing>> existingBuilders = routingMap.get(socketName);
            if (existingBuilders != null) {
                builders.addAll(existingBuilders);
            }

            HttpRouting.Builder listenerHttpRouting = null;
            for (Builder<?, ? extends Routing> builder : builders) {
                if (builder instanceof HttpRouting.Builder httpBuilder) {
                    listenerHttpRouting = new WeightedBuilder(httpBuilder, weight);
                }
            }
            if (listenerHttpRouting == null) {
                listenerHttpRouting = new WeightedBuilder(listener.routing().orElseGet(HttpRouting::builder), weight);
                builders.add(listenerHttpRouting);
            }
            socketToBuilders.put(socketName,
                                 new ListenerBuildersImpl(socketName, serverConfig, listenerHttpRouting, builders, weight));
        });

        return new ServerFeatureContextImpl(serverConfig, socketToBuilders, weight);
    }

    @Override
    public WebServerConfig serverConfig() {
        return serverConfig;
    }

    @Override
    public boolean socketExists(String socketName) {
        return DEFAULT_SOCKET_NAME.equals(socketName) || configuredSockets.contains(socketName);
    }

    @Override
    public Set<String> sockets() {
        return configuredSockets;
    }

    @Override
    public ListenerBuildersImpl socket(String socketName) {
        return Optional.ofNullable(socketToBuilders.get(socketName))
                .orElseThrow(() -> new NoSuchElementException("There is no socket configuration for socket named \""
                                                                      + socketName + "\""));
    }

    void weight(double weight) {
        this.weight.set(weight);
    }

    Router router() {
        return router(DEFAULT_SOCKET_NAME);
    }

    Router router(String socketName) {
        ListenerBuildersImpl listener = socket(socketName);

        boolean containsHttp = listener.routings.stream()
                .anyMatch(it -> it instanceof HttpRouting.Builder);

        Router.Builder builder = Router.builder();
        if (!containsHttp) {
            builder.addRouting(listener.httpRouting());
        }

        return builder.update(it -> listener.routings()
                        .forEach(it::addRouting))
                .build();
    }

    private static HttpRouting.Builder defaultRouting(WebServerConfig serverConfig) {
        HttpRouting.Builder httpRouting = serverConfig.routing().orElse(null);
        if (httpRouting == null) {
            // try to find it in routings
            List<Builder<?, ? extends Routing>> routings = serverConfig.routings();
            for (Builder<?, ? extends Routing> routing : routings) {
                if (routing instanceof HttpRouting.Builder httpBuilder) {
                    httpRouting = httpBuilder;
                }
            }
            if (httpRouting == null) {
                httpRouting = HttpRouting.builder();
            }
        }
        return httpRouting;
    }

    private static class RoutingBuildersImpl implements ServerFeature.RoutingBuilders {
        private final String socketName;
        private final Map<Class<?>, Object> buildersByType;

        RoutingBuildersImpl(String socketName, Map<Class<?>, Object> builders) {
            this.socketName = socketName;
            this.buildersByType = builders;
        }

        static ServerFeature.RoutingBuilders create(String socketName, List<Builder<?, ? extends Routing>> routings) {
            Map<Class<?>, Object> byType = new IdentityHashMap<>();
            for (var routing : routings) {
                byType.put(routing.getClass(), routing);
            }
            return new RoutingBuildersImpl(socketName, byType);
        }

        @Override
        public boolean hasRouting(Class<?> builderType) {
            return buildersByType.containsKey(builderType);
        }

        @Override
        public <T extends Builder<T, ?>> T routingBuilder(Class<T> builderType) {
            Optional<Object> result = Optional.ofNullable(buildersByType.get(builderType));
            if (result.isPresent()) {
                return builderType.cast(result.get());
            }
            throw new NoSuchElementException("There is no routing builder of type "
                                                     + builderType.getName()
                                                     + " available on socket \"" + socketName + "\"");
        }
    }

    private static class ListenerBuildersImpl implements ServerFeature.SocketBuilders {
        private final ListenerConfig listenerConfig;
        private final HttpRouting.Builder httpRouting;
        private final List<Builder<?, ? extends Routing>> routings;
        private final AtomicReference<Double> weight;
        private final ServerFeature.RoutingBuilders routingBuilders;

        ListenerBuildersImpl(String socketName,
                             ListenerConfig listenerConfig,
                             HttpRouting.Builder httpRouting,
                             List<Builder<?, ? extends Routing>> routings,
                             AtomicReference<Double> weight) {
            this.listenerConfig = listenerConfig;
            this.httpRouting = httpRouting;
            this.routings = routings;
            this.weight = weight;

            this.routingBuilders = RoutingBuildersImpl.create(socketName, routings);
        }

        @Override
        public ListenerConfig listener() {
            return listenerConfig;
        }

        @Override
        public HttpRouting.Builder httpRouting() {
            return httpRouting;
        }

        @Override
        public ServerFeature.RoutingBuilders routingBuilders() {
            return routingBuilders;
        }

        List<Builder<?, ? extends Routing>> routings() {
            return routings;
        }
    }

    private static final class WeightedHttpFeature implements HttpFeature, Weighted {
        private final LazyValue<? extends HttpFeature> delegate;
        private final double weight;

        private WeightedHttpFeature(Supplier<? extends HttpFeature> delegate, double weight) {
            this.delegate = LazyValue.create(delegate);
            this.weight = weight;
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            delegate.get().setup(routing);
        }

        @Override
        public String socket() {
            return delegate.get().socket();
        }

        @Override
        public boolean socketRequired() {
            return delegate.get().socketRequired();
        }

        @Override
        public void beforeStart() {
            delegate.get().beforeStart();
        }

        @Override
        public void afterStop() {
            delegate.get().afterStop();
        }

        @Override
        public double weight() {
            return weight;
        }
    }

    /*
    This builder is used as we promise to honor weight of the server feature when adding a feature
    (and it also makes sense). So when for example observability adds an HttpFeature, it will use the same
    weight, and will be ordered as expected.
     */
    private static class WeightedBuilder implements HttpRouting.Builder {
        private final HttpRouting.Builder delegate;
        private final AtomicReference<Double> weight;

        private WeightedBuilder(HttpRouting.Builder delegate, AtomicReference<Double> weight) {
            this.delegate = delegate;
            this.weight = weight;
        }

        @Override
        public HttpRouting.Builder register(HttpService... services) {
            delegate.register(services);
            return this;
        }

        @Override
        public HttpRouting.Builder register(String path, HttpService... services) {
            delegate.register(path, services);
            return this;
        }

        @Override
        public HttpRouting.Builder route(HttpRoute route) {
            delegate.route(route);
            return this;
        }

        @Override
        public HttpRouting.Builder addFilter(Filter filter) {
            delegate.addFilter(filter);
            return this;
        }

        @Override
        public HttpRouting.Builder addFeature(HttpFeature feature) {
            delegate.addFeature(new WeightedHttpFeature(feature, Weights.find(feature, weight.get())));
            return this;
        }

        @Override
        public HttpRouting.Builder addFeature(Supplier<? extends HttpFeature> feature) {
            if (feature instanceof HttpFeature h) {
                return addFeature(h);
            }
            delegate.addFeature(new WeightedHttpFeature(feature, weight.get()));
            return this;
        }

        @Override
        public <T extends Throwable> HttpRouting.Builder error(Class<T> exceptionClass, ErrorHandler<? super T> handler) {
            delegate.error(exceptionClass, handler);
            return this;
        }

        @Override
        public HttpRouting.Builder maxReRouteCount(int maxReRouteCount) {
            delegate.maxReRouteCount(maxReRouteCount);
            return this;
        }

        @Override
        public HttpRouting.Builder security(HttpSecurity security) {
            delegate.security(security);
            return this;
        }

        @Override
        public HttpRouting.Builder copy() {
            delegate.copy();
            return this;
        }

        @Override
        public HttpRouting build() {
            return delegate.build();
        }
    }
}
