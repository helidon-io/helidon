/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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

package io.helidon.grpc.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.grpc.ServerInterceptor;

/**
 * An implementation of {@link io.helidon.grpc.server.GrpcRouting}.
 *
 * @author Aleksandar Seovic
 */
public class GrpcRoutingImpl
        implements GrpcRouting {

    /**
     * The {@link List} of registered {@link ServiceDescriptor} instances.
     */
    private List<ServiceDescriptor> services;

    /**
     * The {@link List} of the global {@link io.grpc.ServerInterceptor}s that should
     * be applied to all services.
     */
    private List<ServerInterceptor> interceptors;

    /**
     * Create a {@link GrpcRoutingImpl}.
     *
     * @param services      the {@link List} of registered {@link ServiceDescriptor} instances
     * @param interceptors  the {@link List} of the global {@link io.grpc.ServerInterceptor}s that should
     *                      be applied to all services
     */
    GrpcRoutingImpl(List<ServiceDescriptor> services, List<ServerInterceptor> interceptors) {
        this.services = new ArrayList<>(Objects.requireNonNull(services));
        this.interceptors = new ArrayList<>(Objects.requireNonNull(interceptors));
    }

    @Override
    public List<ServiceDescriptor> services() {
        return services;
    }

    @Override
    public List<ServerInterceptor> interceptors() {
        return interceptors;
    }
}
