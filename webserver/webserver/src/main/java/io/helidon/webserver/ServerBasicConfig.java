/*
 * Copyright (c) 2017, 2021 Oracle and/or its affiliates.
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
import io.helidon.common.http.ContextualRegistry;

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
    private final ContextualRegistry context;

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
    public ClientAuthentication clientAuth() {
        return socketConfig.clientAuth();
    }

    @Override
    public Set<String> cipherSuite() {
        return socketConfig.cipherSuite();
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
    public int maxHeaderSize() {
        return socketConfig.maxHeaderSize();
    }

    @Override
    public int maxInitialLineLength() {
        return socketConfig.maxInitialLineLength();
    }

    @Override
    public int maxChunkSize() {
        return socketConfig.maxChunkSize();
    }

    @Override
    public boolean validateHeaders() {
        return socketConfig.validateHeaders();
    }

    @Override
    public int initialBufferSize() {
        return socketConfig.initialBufferSize();
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

    static class SocketConfig implements SocketConfiguration {

        private final int port;
        private final InetAddress bindAddress;
        private final int backlog;
        private final int timeoutMillis;
        private final int receiveBufferSize;
        private final SSLContext sslContext;
        private final Set<String> enabledSslProtocols;
        private final Set<String> cipherSuite;
        private final ClientAuthentication clientAuth;
        private final int maxHeaderSize;
        private final int maxInitialLineLength;
        private final int maxChunkSize;
        private final boolean validateHeaders;
        private final int initialBufferSize;

        /**
         * Creates new instance.
         *
         * Used only from builder {@link io.helidon.webserver.SocketConfiguration.Builder}
         *
         * @param builder a builder instance
         */
        SocketConfig(Builder builder) {
            this.port = Math.max(0, builder.port());
            this.bindAddress = builder.bindAddress();
            this.backlog = builder.backlog() <= 0 ? DEFAULT_BACKLOG_SIZE : builder.backlog();
            this.timeoutMillis = Math.max(builder.timeoutMillis(), 0);
            this.receiveBufferSize = Math.max(builder.receiveBufferSize(), 0);
            this.maxHeaderSize = builder.maxHeaderSize();
            this.maxInitialLineLength = builder.maxInitialLineLength();
            this.maxChunkSize = builder.maxChunkSize();
            this.validateHeaders = builder.validateHeaders();
            this.initialBufferSize = builder.initialBufferSize();
            this.clientAuth = builder.clientAuth();
            this.sslContext = builder.sslContext();
            this.cipherSuite = builder.cipherSuite();
            this.enabledSslProtocols = new HashSet<>(builder.enabledSslProtocols());

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
        public Set<String> cipherSuite() {
            return cipherSuite;
        }

        @Override
        public ClientAuthentication clientAuth() {
            return clientAuth;
        }

        @Override
        public int maxHeaderSize() {
            return maxHeaderSize;
        }

        @Override
        public int maxInitialLineLength() {
            return maxInitialLineLength;
        }

        @Override
        public int maxChunkSize() {
            return maxChunkSize;
        }

        @Override
        public boolean validateHeaders() {
            return validateHeaders;
        }

        @Override
        public int initialBufferSize() {
            return initialBufferSize;
        }
    }
}
