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

package io.helidon.webserver.grpc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

@Service.Singleton
class GrpcServerFeature implements ServerFeature {
    private static final String TYPE = "grpc-route-registration";
    private static final System.Logger LOGGER = System.getLogger(GrpcServerFeature.class.getName());

    private final Supplier<List<GrpcRouteRegistration>> routes;
    private final boolean enabled;

    GrpcServerFeature(Config config, Supplier<List<GrpcRouteRegistration>> routes) {
        this.enabled = config.get("server.features." + TYPE + ".enabled").asBoolean().orElse(true);
        this.routes = routes;
    }

    @Override
    public String name() {
        return TYPE;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!enabled) {
            LOGGER.log(System.Logger.Level.TRACE,
                       "gRPC Route Registration Feature disabled "
                               + "- service registry GrpcRouteRegistration instances will be ignored.");
            return;
        }
        for (GrpcRouteRegistration route : routes.get()) {
            SocketBuilders socketBuilders;
            if (!featureContext.socketExists(route.socket())) {
                if (route.socketRequired()) {
                    throw new NoSuchElementException("Socket " + route.socket() + " not found for gRPC route: " + route);
                }
                socketBuilders = featureContext.socket(WebServer.DEFAULT_SOCKET_NAME);
            } else {
                socketBuilders = featureContext.socket(route.socket());
            }

            RoutingBuilders routingBuilders = socketBuilders.routingBuilders();
            GrpcRouting.Builder builder = routingBuilders.routingBuilder(GrpcRouting.Builder.class,
                                                                         GrpcRouting::builder);
            builder.service(route.descriptor());
        }
    }
}
