/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Builder;
import io.helidon.common.socket.SocketOptions;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;
import io.helidon.webserver.spi.TransportBindingFactory;

class WebServerConfigSupport {
    private static final String KEY_BINDINGS = "bindings";
    private static final String KEY_SERVICE_ENABLED = "enabled";

    static class MaxTcpConnectionsDecorator
            implements Prototype.OptionDecorator<ListenerConfig.BuilderBase<?, ?>, Integer> {
        @Override
        public void decorate(ListenerConfig.BuilderBase<?, ?> builder, Integer maxTcpConnections) {
            builder.maxConnections(maxTcpConnections);
        }
    }

    static class CustomMethods {

        /**
         * Add Http routing for an additional socket.
         *
         * @param builder  builder to update
         * @param socket   name of the socket
         * @param consumer HTTP Routing for the given socket name
         */
        @Prototype.BuilderMethod
        static void routing(WebServerConfig.BuilderBase<?, ?> builder,
                            String socket,
                            Consumer<HttpRouting.Builder> consumer) {
            HttpRouting.Builder routingBuilder = HttpRouting.builder();
            consumer.accept(routingBuilder);
            builder.addNamedRouting(socket, routingBuilder);
        }

        /**
         * Add Http routing for an additional socket.
         *
         * @param builder builder to update
         * @param socket  name of the socket
         * @param routing HTTP Routing for the given socket name
         */
        @Prototype.BuilderMethod
        static void routing(WebServerConfig.BuilderBase<?, ?> builder,
                            String socket,
                            HttpRouting.Builder routing) {
            builder.addNamedRouting(socket, routing);
        }
    }

    public static class ServerConfigDecorator implements Prototype.BuilderDecorator<WebServerConfig.BuilderBase<?, ?>> {
        private static final System.Logger LOGGER = System.getLogger(ServerConfigDecorator.class.getName());

        @Override
        public void decorate(WebServerConfig.BuilderBase<?, ?> target) {
            if (target.sockets().containsKey(WebServer.DEFAULT_SOCKET_NAME)) {
                throw new ConfigException("Default socket must be configured directly on server config node, or through"
                                                  + " \"ServerConfig.Builder\", not as a separated socket.");
            }
            if (target.namedRoutings().containsKey(WebServer.DEFAULT_SOCKET_NAME)) {
                throw new ConfigException("Default routing must be configured directly on server config node, or through"
                                                  + " \"ServerConfig.Builder\", not as a named routing.");
            }

            Map<String, ListenerConfig> sockets = target.sockets();
            Map<String, ListenerConfig> namedSockets = new LinkedHashMap<>();
            boolean socketNamesUpdated = false;
            for (Map.Entry<String, ListenerConfig> entry : sockets.entrySet()) {
                String socketName = entry.getKey();
                ListenerConfig socketConfig = entry.getValue();
                if (!socketName.equals(socketConfig.name())) {
                    socketConfig = ListenerConfig.builder(socketConfig)
                            .name(socketName)
                            .buildPrototype();
                    socketNamesUpdated = true;
                }
                namedSockets.put(socketName, socketConfig);
            }
            if (socketNamesUpdated) {
                target.sockets(namedSockets);
            }

            List<ServerFeature> features = target.features();
            List<ServerFeature> uniqueFeatures = new ArrayList<>();
            Set<FeatureId> registeredFeatures = new HashSet<>();
            for (ServerFeature feature : features) {
                if (registeredFeatures.add(new FeatureId(feature.type(), feature.name()))) {
                    uniqueFeatures.add(feature);
                } else {
                    if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
                        LOGGER.log(System.Logger.Level.DEBUG, "Feature (type, name): ("
                                + feature.type() + ", " + feature.name()
                                + ") is already registered with server, and will be ignored (probably one registered through "
                                + "builder, the other through service loader. Builder wins.");
                    }
                }
            }
            target.features(uniqueFeatures);

            Optional<HttpRouting.Builder> routing = target.routing();

            Optional<HttpRouting.Builder> httpRouting = target.routings()
                    .stream()
                    .filter(it -> it instanceof HttpRouting.Builder)
                    .map(HttpRouting.Builder.class::cast)
                    .findFirst();
            if (routing.isPresent() && httpRouting.isPresent()) {
                if (routing.get() != httpRouting.get()) {
                    throw new IllegalStateException(
                            "HTTP routing is configured both through a Builder.routing(...) and builder.routings(...),"
                                    + " which is not compatible.");
                }
            }
        }

        record FeatureId(String type, String name) {
        }
    }

    static class ListenerConfigDecorator implements Prototype.BuilderDecorator<ListenerConfig.BuilderBase<?, ?>> {
        @Override
        public void decorate(ListenerConfig.BuilderBase<?, ?> target) {
            if (target.bindAddress().isPresent()) {
                if (target.address().isEmpty()) {
                    target.address(InetAddress.getLoopbackAddress());
                }
            }
            String name = target.name();
            if (name == null && target.config().isPresent()) {
                var config = target.config().get();
                if (config.exists()) {
                    target.name(config.get("name").asString().orElse(config.name()));
                }
            }
            name = target.name();
            if (name == null) {
                target.name(WebServer.DEFAULT_SOCKET_NAME);
            }
            if (target.bindAddress().orElse(null) instanceof UnixDomainSocketAddress) {
                throw new ConfigException("Listener " + target.name()
                                                  + " uses a Unix domain socket as bind-address. Listener bind-address "
                                                  + "configures TCP endpoints only. Configure the explicit UDS transport "
                                                  + "binding instead: use bindings.uds.socket=/path/to/server.sock; "
                                                  + "for a UDS-only listener also set bindings.tcp.enabled=false. "
                                                  + "With the builder, add a disabled TcpTransportConfig and a "
                                                  + "UdsTransportConfig with "
                                                  + "socket(UnixDomainSocketAddress.of(path)).");
            }

            Optional<Config> listenerConfig = target.config();
            if (listenerConfig.isPresent()) {
                Config tcpConfig = listenerConfig.get()
                        .get(KEY_BINDINGS)
                        .get(TransportBindingTypes.TCP);
                if (tcpConfig.exists()
                        && !tcpConfig.get(KEY_SERVICE_ENABLED).asBoolean().orElse(true)) {
                    boolean hasTcpBinding = false;
                    for (TransportBindingFactory binding : target.bindings()) {
                        if (TransportBindingTypes.TCP.equals(binding.type())) {
                            hasTcpBinding = true;
                            break;
                        }
                    }
                    if (!hasTcpBinding) {
                        TcpTransportConfig tcpBindingConfig = TcpTransportConfig.builder()
                                .enabled(false)
                                .buildPrototype();
                        target.addBinding(TcpTransportBindingFactory.create(tcpBindingConfig));
                    }
                }
            }

            List<TransportBindingFactory> bindings = target.bindings();
            Map<String, TransportBindingFactory> bindingsByType = new LinkedHashMap<>();
            boolean hasTcpBinding = false;
            for (TransportBindingFactory binding : bindings) {
                String type = binding.type();
                if (type == null) {
                    throw new ConfigException("Transport binding factory type must not be null");
                }
                TransportBindingFactory existing = bindingsByType.putIfAbsent(type, binding);
                if (existing != null) {
                    throw new ConfigException("Multiple transport binding factories of type \"" + type
                                                      + "\" are configured for listener \"" + target.name()
                                                      + "\". A listener can have only one transport binding per type.");
                }
                if (TransportBindingTypes.TCP.equals(type)) {
                    hasTcpBinding = true;
                }
            }
            if (!hasTcpBinding) {
                List<TransportBindingFactory> normalizedBindings = new ArrayList<>(bindings.size() + 1);
                normalizedBindings.add(TcpTransportBindingFactory.create(TcpTransportConfig.create()));
                normalizedBindings.addAll(bindings);
                target.bindings(normalizedBindings);
            }

            if (target.connectionOptions().isEmpty()) {
                target.connectionOptions(SocketOptions.create());
            }
            if (target.address().isEmpty()) {
                try {
                    target.address(InetAddress.getByName(target.host()));
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException("Failed to get address from host: " + target.host(), e);
                }
            }
            Map<SocketOption<?>, Object> socketOptions = target.listenerSocketOptions();
            if (target.bindAddress().isEmpty() || !(target.bindAddress().get() instanceof UnixDomainSocketAddress)) {
                if (!socketOptions.containsKey(StandardSocketOptions.SO_REUSEADDR)) {
                    target.putListenerSocketOption(StandardSocketOptions.SO_REUSEADDR, true);
                }
            }
            if (target.requestedUriDiscoveryContext().isEmpty()) {
                target.requestedUriDiscoveryContext(RequestedUriDiscoveryContext.builder()
                                                            .socketId(target.name())
                                                            .build());
            }
        }

    }

    static class ListenerCustomMethods {
        /**
         * Customize HTTP routing of this listener.
         *
         * @param builder         listener config builder
         * @param builderConsumer consumer of HTTP Routing builder
         */
        @Prototype.BuilderMethod
        static void routing(ListenerConfig.BuilderBase<?, ?> builder, Consumer<HttpRouting.Builder> builderConsumer) {
            HttpRouting.Builder routingBuilder = HttpRouting.builder();
            builderConsumer.accept(routingBuilder);
            builder.routing(routingBuilder);
        }

        /**
         * Add a TCP transport binding from configuration.
         *
         * @param builder listener config builder
         * @param config TCP transport binding configuration
         */
        @Prototype.BuilderMethod
        @Prototype.Annotated("io.helidon.common.Api.Incubating")
        static void addBinding(ListenerConfig.BuilderBase<?, ?> builder, TcpTransportConfig config) {
            builder.addBinding(TcpTransportBindingFactory.create(config));
        }

        /**
         * Add a Unix domain socket transport binding from configuration.
         *
         * @param builder listener config builder
         * @param config Unix domain socket transport binding configuration
         */
        @Prototype.BuilderMethod
        @Prototype.Annotated("io.helidon.common.Api.Incubating")
        static void addBinding(ListenerConfig.BuilderBase<?, ?> builder, UdsTransportConfig config) {
            builder.addBinding(UdsTransportBindingFactory.create(config));
        }

        @Prototype.ConfigFactoryMethod("bindAddress")
        static SocketAddress createBindAddress(Config config) {
            String address = config.asString().get();
            if (address.startsWith("unix:/")) {
                return UnixDomainSocketAddress.of(address.substring("unix:".length()));
            }
            int col = address.indexOf(':');
            // must be localhost:8080 or similar
            String host;
            int port;
            if (col == 0) {
                host = "localhost";
                port = Integer.parseInt(address.substring(1));
            } else if (col > 0) {
                host = address.substring(0, col);
                port = Integer.parseInt(address.substring(col + 1));
            } else {
                host = address;
                port = 0;
            }
            return new InetSocketAddress(host, port);
        }
    }

    static class RoutingsDecorator implements Prototype.OptionDecorator<ListenerConfig.BuilderBase<?, ?>,
            io.helidon.common.Builder<?, ? extends Routing>> {
        @Override
        public void decorate(ListenerConfig.BuilderBase<?, ?> builder, Builder<?, ? extends Routing> optionValue) {
            if (optionValue instanceof HttpRouting.Builder httpRouting) {
                if (builder.routings().isEmpty()) {
                    builder.routing(httpRouting);
                } else {
                    throw new IllegalStateException("HTTP routing is configured both through routing() "
                                                            + "and routings() method. This would end up with two distinct"
                                                            + " HTTP routings for a single listener, which is not allowed.");
                }
            }
        }

        @Override
        public void decorateSetList(ListenerConfig.BuilderBase<?, ?> builder, List<Builder<?, ? extends Routing>> optionValues) {
            for (Builder<?, ? extends Routing> optionValue : optionValues) {
                if (optionValue instanceof HttpRouting.Builder httpRouting) {
                    if (builder.routing().isEmpty()) {
                        builder.routing(httpRouting);
                    } else {
                        // intentional instance equality - if the routing is the same instance, there is no issue
                        if (builder.routing().get() != httpRouting) {
                            throw new IllegalStateException("HTTP routing is configured both through routing() "
                                                                    + "and routings() method. This would end up with two distinct"
                                                                    + " HTTP routings for a single listener, which is not "
                                                                    + "allowed.");
                        }
                    }
                }
            }
        }

        @Override
        public void decorateAddList(ListenerConfig.BuilderBase<?, ?> builder, List<Builder<?, ? extends Routing>> optionValues) {
            decorateSetList(builder, optionValues);
        }
    }
}
