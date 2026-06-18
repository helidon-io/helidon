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
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.service.registry.Service;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * Adds generated declarative gRPC route registrations to WebServer gRPC routing.
 */
@Service.Singleton
public class GrpcServerFeatureProvider implements ServerFeatureProvider<GrpcServerFeature> {
    private static final String TYPE = "grpc-route-registration";

    private final Supplier<List<GrpcRouteRegistration>> routes;

    GrpcServerFeatureProvider(Supplier<List<GrpcRouteRegistration>> routes) {
        this.routes = routes;
    }

    @Override
    public String configKey() {
        return TYPE;
    }

    @Override
    public GrpcServerFeature create(Config config, String name) {
        return new GrpcServerFeature(config, routes);
    }
}
