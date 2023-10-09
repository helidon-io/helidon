package io.helidon.webserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.helidon.common.Builder;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;

import static io.helidon.webserver.WebServer.DEFAULT_SOCKET_NAME;

class ServerFeatureContextImpl implements ServerFeature.ServerFeatureContext {
    private final WebServerConfig serverConfig;
    private final Map<String, ListenerBuildersImpl> socketToBuilders;
    private final Set<String> configuredSockets;

    private ServerFeatureContextImpl(WebServerConfig serverConfig, Map<String, ListenerBuildersImpl> socketToBuilders) {
        this.serverConfig = serverConfig;
        this.socketToBuilders = socketToBuilders;
        this.configuredSockets = socketToBuilders.keySet()
                .stream()
                .filter(Predicate.not(DEFAULT_SOCKET_NAME::equals))
                .collect(Collectors.toSet());
    }

    static ServerFeatureContextImpl create(WebServerConfig serverConfig) {
        Map<String, List<Builder<?, ? extends Routing>>> routingMap = serverConfig.namedRoutings();
        Map<String, ListenerConfig> sockets = serverConfig.sockets();
        HttpRouting.Builder httpRouting = defaultRouting(serverConfig);
        List<Builder<?, ? extends Routing>> routings = serverConfig.routings();

        Map<String, ListenerBuildersImpl> socketToBuilders = new HashMap<>();
        socketToBuilders.put(DEFAULT_SOCKET_NAME,
                             new ListenerBuildersImpl(DEFAULT_SOCKET_NAME, serverConfig, httpRouting, routings));
        sockets.forEach((socketName, listener) -> {
            List<Builder<?, ? extends Routing>> builders = new ArrayList<>();

            List<Builder<?, ? extends Routing>> existingBuilders = routingMap.get(socketName);
            if (existingBuilders == null) {
                builders.addAll(listener.routings());
            } else {
                builders.addAll(existingBuilders);
            }

            HttpRouting.Builder listenerHttpRouting = null;
            for (Builder<?, ? extends Routing> builder : builders) {
                if (builder instanceof HttpRouting.Builder httpBuilder) {
                    listenerHttpRouting = httpBuilder;
                }
            }
            if (listenerHttpRouting == null) {
                listenerHttpRouting = listener.routing().orElseGet(httpRouting::copy);
                builders.add(listenerHttpRouting);
            }
            socketToBuilders.put(socketName,
                                 new ListenerBuildersImpl(socketName, serverConfig, listenerHttpRouting, builders));
        });

        return new ServerFeatureContextImpl(serverConfig, socketToBuilders);
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

    @Override
    public WebServerConfig serverConfig() {
        return serverConfig;
    }

    @Override
    public ListenerBuildersImpl defaultListener() {
        return socket(DEFAULT_SOCKET_NAME);
    }

    @Override
    public boolean socketExists(String socketName) {
        return socketToBuilders.containsKey(socketName);
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

    Router router() {
        return router(DEFAULT_SOCKET_NAME);
    }

    Router router(String socketName) {
        ListenerBuildersImpl listener = socket(socketName);

        return Router.builder()
                .addRouting(listener.httpRouting())
                .update(it -> listener.routings()
                        .forEach(it::addRouting))
                .build();
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
            return builderType.cast(Optional.ofNullable(buildersByType.get(builderType))
                                            .orElseThrow(() -> new NoSuchElementException("There is no routing builder of type "
                                                                                                  + builderType.getName()
                                                                                                  + " available on socket \"" + socketName + "\"")));
        }
    }

    private static class ListenerBuildersImpl implements ServerFeature.SocketBuilders {
        private final ListenerConfig listenerConfig;
        private final HttpRouting.Builder httpRouting;
        private final List<Builder<?, ? extends Routing>> routings;
        private final ServerFeature.RoutingBuilders routingBuilders;

        ListenerBuildersImpl(String socketName,
                             ListenerConfig listenerConfig,
                             HttpRouting.Builder httpRouting,
                             List<Builder<?, ? extends Routing>> routings) {
            this.listenerConfig = listenerConfig;
            this.httpRouting = httpRouting;
            this.routings = routings;

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
}
