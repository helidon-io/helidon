/*
 * Copyright (c) 2017, 2020 Oracle and/or its affiliates.
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.helidon.common.context.Context;

import io.netty.handler.ssl.ClientAuth;
import io.opentracing.Tracer;

/**
 * Basic implementation of the {@link ServerConfiguration}.
 */
class ServerBasicConfig implements ServerConfiguration {
    private final SocketConfiguration socketConfig;
    private final int workers;
    private final Tracer tracer;
    private final Map<String, SocketConfiguration> socketConfigs;
    private final ExperimentalConfiguration experimental;
    private final Context context;
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
        map.put(WebServer.DEFAULT_SOCKET_NAME, this.socketConfig);
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
    public ClientAuth clientAuth() {
        return socketConfig.clientAuth();
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
        private final String name;
        private final boolean enabled;
        private final ClientAuth clientAuth;

        /**
         * Creates new instance.
         */
        SocketConfig(SocketConfiguration.Builder builder) {
            this.name = builder.name();
            this.enabled = builder.enabled();
            this.port = Math.max(builder.port(), 0);
            this.bindAddress = builder.bindAddress().orElse(null);
            this.backlog = builder.backlog() < 0 ? DEFAULT_BACKLOG_SIZE : builder.backlog();
            this.timeoutMillis = Math.max(builder.timeoutMillis(), 0);
            this.receiveBufferSize = Math.max(builder.receiveBufferSize(), 0);
            TlsConfig tlsConfig = builder.tlsConfig();
            if (tlsConfig.enabled()) {
                this.sslContext = tlsConfig.sslContext();
                this.enabledSslProtocols = new HashSet<>(tlsConfig.enabledTlsProtocols());
                this.clientAuth = tlsConfig.clientAuth();
            } else {
                this.sslContext = null;
                this.enabledSslProtocols = Set.of();
                this.clientAuth = ClientAuth.NONE;
            }
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

        @Override
        public ClientAuth clientAuth() {
            return clientAuth;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean enabled() {
            return enabled;
        }
    }
}
