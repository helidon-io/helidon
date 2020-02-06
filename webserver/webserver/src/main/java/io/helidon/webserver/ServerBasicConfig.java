/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.helidon.common.context.Context;

import io.opentracing.Tracer;

/**
 * Basic implementation of the {@link ServerConfiguration}.
 */
@SuppressWarnings("deprecation")
class ServerBasicConfig implements ServerConfiguration {
    private final SocketConfiguration socketConfig;
    private final int workers;
    private final Tracer tracer;
    private final Map<String, SocketConfiguration> socketConfigs;
    private final ExperimentalConfiguration experimental;
    private final io.helidon.common.http.ContextualRegistry context;
    private final boolean printFeatureDetails;

    /**
     * Creates new instance.
     *
     * @param builder configuration builder
     */
    ServerBasicConfig(ServerConfiguration.Builder builder) {
        this.socketConfig = builder.defaultSocketBuilder().build();
        this.workers = builder.workers();
        this.tracer = builder.tracer();
        this.experimental = builder.experimental();
        this.context = builder.context();
        this.printFeatureDetails = builder.printFeatureDetails();

        HashMap<String, SocketConfiguration> map = new HashMap<>(builder.sockets());
        map.put(ServerConfiguration.DEFAULT_SOCKET_NAME, this.socketConfig);
        this.socketConfigs = Collections.unmodifiableMap(map);
    }

    @Override
    public SSLContext ssl() {
        return socketConfig.ssl();
    }

    @Override
    public Set<String> enabledSslProtocols() {
        return socketConfig.enabledSslProtocols();
    }

    @Override
    public int workersCount() {
        return workers;
    }

    @Override
    public int port() {
        return socketConfig.port();
    }

    @Override
    public InetAddress bindAddress() {
        return socketConfig.bindAddress();
    }

    @Override
    public int backlog() {
        return socketConfig.backlog();
    }

    @Override
    public int timeoutMillis() {
        return socketConfig.timeoutMillis();
    }

    @Override
    public int receiveBufferSize() {
        return socketConfig.receiveBufferSize();
    }

    @Override
    public Tracer tracer() {
        return tracer;
    }

    @Override
    public Map<String, SocketConfiguration> sockets() {
        return socketConfigs;
    }

    @Override
    public ExperimentalConfiguration experimental() {
        return experimental;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public boolean printFeatureDetails() {
        return printFeatureDetails;
    }

    static class SocketConfig implements SocketConfiguration {

        private final int port;
        private final InetAddress bindAddress;
        private final int backlog;
        private final int timeoutMillis;
        private final int receiveBufferSize;
        private final SSLContext sslContext;
        private final Set<String> enabledSslProtocols;

        /**
         * Creates new instance.
         *
         * @param port              a server port - ff port is {@code 0} or less then any available ephemeral port will be used
         * @param bindAddress       an address to bind the server or {@code null} for all local addresses
         * @param sslContext        the ssl context to associate with this socket configuration
         * @param backlog           a maximum length of the queue of incoming connections
         * @param timeoutMillis     a socket timeout in milliseconds or {@code 0} for infinite
         * @param receiveBufferSize proposed TCP receive window size in bytes
         */
        SocketConfig(int port,
                     InetAddress bindAddress,
                     SSLContext sslContext,
                     Set<String> sslProtocols,
                     int backlog,
                     int timeoutMillis,
                     int receiveBufferSize) {
            this.port = port <= 0 ? 0 : port;
            this.bindAddress = bindAddress;
            this.backlog = backlog <= 0 ? DEFAULT_BACKLOG_SIZE : backlog;
            this.timeoutMillis = timeoutMillis <= 0 ? 0 : timeoutMillis;
            this.receiveBufferSize = receiveBufferSize <= 0 ? 0 : receiveBufferSize;
            this.sslContext = sslContext;
            this.enabledSslProtocols = sslProtocols;
        }

        /**
         * Creates default values instance.
         */
        SocketConfig() {
            this(0, null, null, null, 0, 0, 0);
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public InetAddress bindAddress() {
            return bindAddress;
        }

        @Override
        public int backlog() {
            return backlog;
        }

        @Override
        public int timeoutMillis() {
            return timeoutMillis;
        }

        @Override
        public int receiveBufferSize() {
            return receiveBufferSize;
        }

        @Override
        public SSLContext ssl() {
            return sslContext;
        }

        @Override
        public Set<String> enabledSslProtocols() {
            return enabledSslProtocols;
        }
    }
}
