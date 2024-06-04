/*
 * Copyright (c) 2019, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.grpc.server;

import java.time.Duration;

import io.helidon.common.context.Context;

/**
 * Configuration class for the {@link GrpcServer} implementations.
 */
public class GrpcServerBasicConfig
        implements GrpcServerConfiguration {

    private final String name;

    private final int port;

    private final boolean nativeTransport;

    private final int workers;

    private final GrpcTlsDescriptor tlsConfig;

    private final Context context;

    private final int maxRapidResets;

    private final Duration rapidResetCheckPeriod;

    /**
     * Construct {@link GrpcServerBasicConfig} instance.
     *
     * @param builder the {@link GrpcServerConfiguration.Builder} to use to configure
     * this {@link GrpcServerBasicConfig}.
     */
    private GrpcServerBasicConfig(GrpcServerConfiguration.Builder builder) {
        this.name = builder.name();
        this.port = builder.port();
        this.context = builder.context();
        this.nativeTransport = builder.useNativeTransport();
        this.workers = builder.workers();
        this.tlsConfig = builder.tlsConfig();
        this.maxRapidResets = builder.maxRapidResets();
        this.rapidResetCheckPeriod = builder.rapidResetCheckPeriod();
    }

    /**
     * Create a {@link GrpcServerBasicConfig} instance using the specified builder.
     *
     * @param builder the {@link GrpcServerConfiguration.Builder} to use to configure
     * this {@link GrpcServerBasicConfig}
     * @return a {@link GrpcServerBasicConfig} instance
     */
    static GrpcServerBasicConfig create(GrpcServerConfiguration.Builder builder) {
        return new GrpcServerBasicConfig(builder);
    }

    // ---- accessors ---------------------------------------------------

    /**
     * Get the server name.
     *
     * @return the server name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Get the server port.
     *
     * @return the server port
     */
    @Override
    public int port() {
        return port;
    }

    @Override
    public Context context() {
        return context;
    }

    /**
     * Determine whether use native transport if possible.
     * <p>
     * If native transport support is enabled, gRPC server will use epoll on
     * Linux, or kqueue on OS X. Otherwise, the standard NIO transport will
     * be used.
     *
     * @return {@code true} if native transport should be used
     */
    @Override
    public boolean useNativeTransport() {
        return nativeTransport;
    }

    @Override
    public int workers() {
        return workers;
    }

    @Override
    public GrpcTlsDescriptor tlsConfig() {
        return tlsConfig;
    }

    @Override
    public Duration rapidResetCheckPeriod() {
        return rapidResetCheckPeriod;
    }

    @Override
    public int maxRapidResets() {
        return maxRapidResets;
    }
}
