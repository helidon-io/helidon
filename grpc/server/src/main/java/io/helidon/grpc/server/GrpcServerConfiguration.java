/*
 * Copyright (c) 2016 Oracle and/or its affiliates. All rights reserved.
 */
package io.helidon.grpc.server;

import io.helidon.config.Config;
import io.helidon.webserver.ServerConfiguration;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import java.util.function.Supplier;

/**
 * @author jk  2019.03.05
 */
public interface GrpcServerConfiguration
    {
    /**
     * The default server name.
     */
    String DEFAULT_NAME = "grpc.server";

    /**
     * The default grpc port.
     */
    int DEFAULT_PORT = 1408;

    /**
     * Get the server name.
     *
     * @return the server name
     */
    String name();

    /**
     * Get the server port.
     *
     * @return the server port
     */
    int port();

    /**
     * Determine whether use native transport if possible.
     * <p/>
     * If native transport support is enabled, gRPC server will use epoll on
     * Linux, or kqueue on OS X. Otherwise, the standard NIO transport will
     * be used.
     *
     * @return {@code true} if native transport should be used
     */
    boolean useNativeTransport();

    /**
     * Determine whether TLS is enabled.
     *
     * @return {@code true} if TLS is enabled
     */
    boolean isTLS();

    /**
     * Obtain the location of the TLS certs file to use.
     *
     * @return the location of the TLS certs file to use
     */
    String tlsCert();

    /**
     * Obtain the location of the TLS key file to use.
     *
     * @return the location of the TLS key file to use
     */
    String tlsKey();

    /**
     * Obtain the location of the TLS CA certs file to use.
     *
     * @return the location of the TLS CA certs file to use
     */
    String tlsCaCert();

    /**
     * Returns an <a href="http://opentracing.io">opentracing.io</a> tracer. Default is {@link GlobalTracer}.
     *
     * @return a tracer to use - never {@code null} (defaulting to {@link GlobalTracer}
     */
    Tracer tracer();

    /**
     * Creates new instance with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new instance
     */
    static GrpcServerConfiguration create(Config config) {
        return builder(config).build();
    }

static GrpcServerBasicConfig defaultConfig()
    {
    return new GrpcServerBasicConfig(DEFAULT_NAME, DEFAULT_PORT);
    }


    /**
     * Creates new instance of a {@link Builder server configuration builder}.
     *
     * @return a new builder instance
     */
    static GrpcServerConfiguration.Builder builder() {
        return new Builder();
    }

    /**
     * Creates new instance of a {@link Builder server configuration builder} with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new builder instance
     */
    static Builder builder(Config config) {
        return new Builder().config(config);
    }

    /**
     * A {@link GrpcServerConfiguration} builder.
     */
    final class Builder implements io.helidon.common.Builder<GrpcServerConfiguration>
        {
        private String name;

        private int port;

        private boolean useNativeTransport;

        private boolean useTLS;

        private String tlsCert;

        private String tlsKey;

        private String tlsCACert;

        private Tracer tracer;

        private Builder()
            {
            }

        public GrpcServerConfiguration.Builder config(Config config)
            {
            if (config == null)
                {
                return this;
                }

            name = config.get("name").asString().orElse(DEFAULT_NAME);
            port = config.get("port").asInt().orElse(DEFAULT_PORT);
            useNativeTransport = config.get("native").asBoolean().orElse(false);

            Config cfgTLS = config.get("tls");

            if (cfgTLS != null)
                {
                useTLS = cfgTLS.get("enabled").asBoolean().orElse(false);
                tlsCert = cfgTLS.get("cert").asString().orElse(null);
                tlsKey = cfgTLS.get("key").asString().orElse(null);
                tlsCACert = cfgTLS.get("cacert").asString().orElse(null);
                }

            return this;
            }

        /**
         * Sets an <a href="http://opentracing.io">opentracing.io</a> tracer. (Default is {@link GlobalTracer}.)
         *
         * @param tracer a tracer to set
         * @return an updated builder
         */
        public Builder tracer(Tracer tracer)
            {
            this.tracer = tracer;
            return this;
            }

        /**
         * Sets an <a href="http://opentracing.io">opentracing.io</a> tracer. (Default is {@link GlobalTracer}.)
         *
         * @param tracerBuilder a tracer builder to set; will be built as a first step of this method execution
         * @return updated builder
         */
        public Builder tracer(Supplier<? extends Tracer> tracerBuilder)
            {
            this.tracer = tracerBuilder != null ? tracerBuilder.get() : null;
            return this;
            }

        @Override
        public GrpcServerConfiguration build()
            {
            return new GrpcServerBasicConfig(name, port, useNativeTransport, useTLS, tlsCert, tlsKey, tlsCACert, tracer);
            }
        }
    }
