/*
 * Copyright (c) 2017, 2018 Oracle and/or its affiliates. All rights reserved.
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

import javax.net.ssl.SSLContext;

import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;

/**
 * Basic implementation of the {@link ServerConfiguration}.
 */
class ServerBasicConfig implements ServerConfiguration {

    static final ServerConfiguration DEFAULT_CONFIGURATION = ServerConfiguration.builder().build();

    private final SocketConfiguration socketConfig;
    private final int workers;
    private final Tracer tracer;
    private final Map<String, SocketConfiguration> socketConfigs;
    private final ExperimentalConfiguration experimental;

    /**
     * Creates new instance.
     *
     * @param socketConfig  a default socket configuration values
     * @param workers       a count of threads in a pool used to tryProcess HTTP requests
     * @param tracer        an {@code opentracing.io} tracer
     * @param socketConfigs socket configurations of additional ports to listen on
     */
    ServerBasicConfig(SocketConfiguration socketConfig,
                      int workers,
                      Tracer tracer,
                      Map<String, SocketConfiguration> socketConfigs,
                      ExperimentalConfiguration experimental) {
        this.socketConfig = socketConfig == null ? new SocketConfig() : socketConfig;
        if (workers <= 0) {
            workers = Runtime.getRuntime().availableProcessors() * 2;
        }
        this.workers = workers;
        this.tracer = tracer == null ? GlobalTracer.get() : tracer;
        HashMap<String, SocketConfiguration> map = new HashMap<>(socketConfigs);
        map.put(ServerConfiguration.DEFAULT_SOCKET_NAME, this.socketConfig);
        this.socketConfigs = Collections.unmodifiableMap(map);
        this.experimental = experimental != null ? experimental
                : new ExperimentalConfiguration.Builder().build();
    }

    @Override
    public SSLContext ssl() {
        return socketConfig.ssl();
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

    static class SocketConfig implements SocketConfiguration {

        private final int port;
        private final InetAddress bindAddress;
        private final int backlog;
        private final int timeoutMillis;
        private final int receiveBufferSize;
        private final SSLContext sslContext;

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
                     int backlog,
                     int timeoutMillis,
                     int receiveBufferSize) {
            this.port = port <= 0 ? 0 : port;
            this.bindAddress = bindAddress;
            this.backlog = backlog <= 0 ? DEFAULT_BACKLOG_SIZE : backlog;
            this.timeoutMillis = timeoutMillis <= 0 ? 0 : timeoutMillis;
            this.receiveBufferSize = receiveBufferSize <= 0 ? 0 : receiveBufferSize;
            this.sslContext = sslContext;
        }

        /**
         * Creates default values instance.
         */
        SocketConfig() {
            this(0, null, null, 0, 0, 0);
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
    }
}
