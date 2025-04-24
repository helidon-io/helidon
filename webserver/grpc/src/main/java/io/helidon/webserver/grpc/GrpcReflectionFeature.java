/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.grpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.webserver.Routing;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

import static java.lang.System.Logger.Level;

/**
 * Adds gRPC reflection support to Helidon WebServer.
 */
@RuntimeType.PrototypedBy(GrpcReflectionConfig.class)
public class GrpcReflectionFeature implements Weighted, ServerFeature, RuntimeType.Api<GrpcReflectionConfig> {
    private static final System.Logger LOGGER = System.getLogger(GrpcReflectionFeature.class.getName());

    static final String GRPC_REFLECTION = "grpc-reflection";
    static final Map<String, List<GrpcRouting>> SOCKET_GRPC_ROUTINGS = new HashMap<>();

    private final GrpcReflectionConfig config;

    GrpcReflectionFeature(GrpcReflectionConfig config) {
        this.config = config;
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static GrpcReflectionConfig.Builder builder() {
        return GrpcReflectionConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static GrpcReflectionFeature create(GrpcReflectionConfig config) {
        return new GrpcReflectionFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static GrpcReflectionFeature create(Consumer<GrpcReflectionConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create a new gRPC reflection feature with default setup.
     *
     * @return a new feature
     */
    public static GrpcReflectionFeature create() {
        return builder().build();
    }

    /**
     * Create a new gRPC reflection feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static GrpcReflectionFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!config.enabled()) {
            return;
        }

        Set<String> sockets = new HashSet<>(config.sockets());
        if (sockets.isEmpty()) {
            sockets.addAll(featureContext.sockets());
            sockets.add(WebServer.DEFAULT_SOCKET_NAME);
        }

        sockets.forEach(socket -> {
            SocketBuilders sb = featureContext.socket(socket);

            // adds gRPC reflection service
            if (sb.routingBuilders().hasRouting(GrpcRouting.Builder.class)) {
                sb.routingBuilders()
                        .routingBuilder(GrpcRouting.Builder.class)
                        .service(new GrpcReflectionService(socket));
            } else {
                LOGGER.log(Level.WARNING, "Unable to register gRPC reflection service, "
                        + "no gRPC routes found for socket " + socket);
            }

            // collects per-socket map of all gRPC routes found
            sb.listener()
                    .routings()
                    .forEach(routingBuilder -> {
                        Routing routing = routingBuilder.build();
                        if (routing instanceof GrpcRouting grpcRouting) {
                            List<GrpcRouting> grpcRoutings = SOCKET_GRPC_ROUTINGS
                                    .computeIfAbsent(socket, k -> new ArrayList<>());
                            grpcRoutings.add(grpcRouting);
                        }
                    });
        });
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return GRPC_REFLECTION;
    }

    @Override
    public GrpcReflectionConfig prototype() {
        return config;
    }

    static Map<String, List<GrpcRouting>> socketGrpcRoutings() {
        return SOCKET_GRPC_ROUTINGS;
    }
}
