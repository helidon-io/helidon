/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.Prototype;
import io.helidon.common.Builder;
import io.helidon.common.config.Config;
import io.helidon.common.socket.SocketOptions;
import io.helidon.config.ConfigException;
import io.helidon.http.RequestedUriDiscoveryContext;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;

class WebServerConfigSupport {

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
            String name = target.name();
            if (name == null && target.config().isPresent()) {
                Config config = target.config().get();
                if (config.exists()) {
                    target.name(config.get("name").asString().orElse(config.name()));
                }
            }
            name = target.name();
            if (name == null) {
                target.name(WebServer.DEFAULT_SOCKET_NAME);
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
            if (!socketOptions.containsKey(StandardSocketOptions.SO_REUSEADDR)) {
                target.putListenerSocketOption(StandardSocketOptions.SO_REUSEADDR, true);
            }
            if (!socketOptions.containsKey(StandardSocketOptions.SO_RCVBUF)) {
                target.putListenerSocketOption(StandardSocketOptions.SO_RCVBUF, 4096);
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
