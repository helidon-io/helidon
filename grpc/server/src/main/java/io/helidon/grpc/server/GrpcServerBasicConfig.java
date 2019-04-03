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

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * Configuration class for the {@link GrpcServer} implementations.
 */
public class GrpcServerBasicConfig
        implements GrpcServerConfiguration {

    private final String name;

    private final int port;

    private final boolean nativeTransport;

    private final Tracer tracer;

    private final TracingConfiguration tracingConfig;

    private final int workers;

    private final SslConfiguration sslConfig;

    /**
     * Construct {@link GrpcServerBasicConfig} instance.
     *
     * @param name            the server name
     * @param port            the port to listen on
     * @param workers         a count of threads in a pool used to tryProcess HTTP requests
     * @param nativeTransport {@code true} to enable native transport for
     *                        the server
     * @param tracer          the tracer to use
     * @param tracingConfig   the tracing configuration
     * @param sslConfig       the SSL configuration
     */
    public GrpcServerBasicConfig(String name,
                                 int port,
                                 int workers,
                                 boolean nativeTransport,
                                 Tracer tracer,
                                 TracingConfiguration tracingConfig,
                                 SslConfiguration sslConfig) {

        this.name = name == null || name.trim().isEmpty() ? DEFAULT_NAME : name.trim();
        this.port = port <= 0 ? 0 : port;
        this.nativeTransport = nativeTransport;
        this.tracer = tracer == null ? GlobalTracer.get() : tracer;
        this.tracingConfig = tracingConfig == null ? new TracingConfiguration.Builder().build() : tracingConfig;
        this.workers = workers > 0 ? workers : DEFAULT_WORKER_COUNT;
        this.sslConfig = sslConfig;
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
    public Tracer tracer() {
        return tracer;
    }

    @Override
    public TracingConfiguration tracingConfig() {
        return tracingConfig;
    }

    @Override
    public int workers() {
        return workers;
    }

    @Override
    public SslConfiguration sslConfig() {
        return sslConfig;
    }
}
