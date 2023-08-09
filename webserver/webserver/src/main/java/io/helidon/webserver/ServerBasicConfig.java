/*
 * Copyright (c) 2017, 2023 Oracle and/or its affiliates.
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
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.net.ssl.SSLContext;

import io.helidon.common.configurable.AllowList;
import io.helidon.common.context.Context;
import io.helidon.tracing.Tracer;

/**
 * Basic implementation of the {@link ServerConfiguration}.
 */
class ServerBasicConfig implements ServerConfiguration {
    private final SocketConfiguration socketConfig;
    private final int workers;
    private final Tracer tracer;
    private final Map<String, SocketConfiguration> socketConfigs;
    private final Duration maxShutdownTimeout;
    private final Duration shutdownQuietPeriod;
    private final Optional<Transport> transport;
    private final Context context;
    private final boolean printFeatureDetails;
    private final AllowList trustedProxies;
    private final boolean isRequestedUriDiscoveryEnabled;

    /**
     * Creates new instance.
     *
     * @param builder configuration builder
     */
    ServerBasicConfig(ServerConfiguration.Builder builder) {
        this.socketConfig = builder.defaultSocketBuilder().build();
        this.workers = builder.workers();
        this.tracer = builder.tracer();
        this.maxShutdownTimeout = builder.maxShutdownTimeout();
        this.shutdownQuietPeriod = builder.shutdownQuietPeriod();
        this.transport = builder.transport();
        this.context = builder.context();
        this.printFeatureDetails = builder.printFeatureDetails();
        this.trustedProxies = builder.defaultSocketBuilder().trustedProxies();
        this.isRequestedUriDiscoveryEnabled = builder.defaultSocketBuilder().requestedUriDiscoveryEnabled();

        HashMap<String, SocketConfiguration> map = new HashMap<>(builder.sockets());
        map.put(WebServer.DEFAULT_SOCKET_NAME, this.socketConfig);
        this.socketConfigs = Collections.unmodifiableMap(map);
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public SSLContext ssl() {
        return socketConfig.ssl();
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public Set<String> enabledSslProtocols() {
        return socketConfig.enabledSslProtocols();
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public Set<String> allowedCipherSuite() {
        return socketConfig.allowedCipherSuite();
    }

    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public ClientAuthentication clientAuth() {
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
    public Optional<WebServerTls> tls() {
        return socketConfig.tls();
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
    public Duration maxShutdownTimeout() {
        return maxShutdownTimeout;
    }

    @Override
    public Duration shutdownQuietPeriod() {
        return shutdownQuietPeriod;
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
    public Optional<Transport> transport() {
        return transport;
    }

    @Override
    public Context context() {
        return context;
    }

    @Override
    public boolean printFeatureDetails() {
        return printFeatureDetails;
    }

    @Override
    public boolean enableCompression() {
        return socketConfig.enableCompression();
    }

    @Override
    public List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes() {
        return socketConfig.requestedUriDiscoveryTypes();
    }

    @Override
    public AllowList trustedProxies() {
        return trustedProxies;
    }

    @Override
    public boolean requestedUriDiscoveryEnabled() {
        return isRequestedUriDiscoveryEnabled;
    }

    static class SocketConfig implements SocketConfiguration {

        private final int port;
        private final InetAddress bindAddress;
        private final int backlog;
        private final int timeoutMillis;
        private final int receiveBufferSize;
        private final WebServerTls webServerTls;
        private final String name;
        private final boolean enabled;
        private final int maxHeaderSize;
        private final int maxInitialLineLength;
        private final int maxChunkSize;
        private final boolean validateHeaders;
        private final int initialBufferSize;
        private final boolean enableCompression;
        private final long maxPayloadSize;
        private final long backpressureBufferSize;
        private final BackpressureStrategy backpressureStrategy;
        private final boolean continueImmediately;
        private final int maxUpgradeContentLength;
        private final List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes;
        private final AllowList trustedProxies;
        private final boolean isRequestedUriDiscoveryEnabled;

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
            this.maxHeaderSize = builder.maxHeaderSize();
            this.maxInitialLineLength = builder.maxInitialLineLength();
            this.maxChunkSize = builder.maxChunkSize();
            this.validateHeaders = builder.validateHeaders();
            this.initialBufferSize = builder.initialBufferSize();
            this.enableCompression = builder.enableCompression();
            this.maxPayloadSize = builder.maxPayloadSize();
            this.backpressureBufferSize = builder.backpressureBufferSize();
            this.backpressureStrategy = builder.backpressureStrategy();
            this.continueImmediately = builder.continueImmediately();
            this.maxUpgradeContentLength = builder.maxUpgradeContentLength();
            WebServerTls webServerTls = builder.tlsConfig();
            this.webServerTls = webServerTls.enabled() ? webServerTls : null;
            this.requestedUriDiscoveryTypes = builder.requestedUriDiscoveryTypes();
            this.trustedProxies = builder.trustedProxies();
            this.isRequestedUriDiscoveryEnabled = builder.requestedUriDiscoveryEnabled();
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
        public Optional<WebServerTls> tls() {
            return Optional.ofNullable(webServerTls);
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public SSLContext ssl() {
            return tls().map(WebServerTls::sslContext).orElse(null);
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public Set<String> enabledSslProtocols() {
            return tls().map(WebServerTls::enabledTlsProtocols).map(Set::copyOf).orElseGet(Set::of);
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public Set<String> allowedCipherSuite() {
            return tls().map(WebServerTls::cipherSuite).orElseGet(Set::of);
        }

        @Override
        @SuppressWarnings({"deprecation", "removal"})
        public ClientAuthentication clientAuth() {
            return tls().map(WebServerTls::clientAuth).orElse(ClientAuthentication.NONE);
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean enabled() {
            return enabled;
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

        @Override
        public int maxUpgradeContentLength() {
            return maxUpgradeContentLength;
        }

        @Override
        public boolean enableCompression() {
            return enableCompression;
        }

        @Override
        public long maxPayloadSize() {
            return maxPayloadSize;
        }

        @Override
        public long backpressureBufferSize() {
            return backpressureBufferSize;
        }

        @Override
        public BackpressureStrategy backpressureStrategy() {
            return backpressureStrategy;
        }

        @Override
        public boolean continueImmediately() {
            return continueImmediately;
        }

        @Override
        public List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes() {
            return requestedUriDiscoveryTypes;
        }

        @Override
        public AllowList trustedProxies() {
            return trustedProxies;
        }

        @Override
        public boolean requestedUriDiscoveryEnabled() {
            return isRequestedUriDiscoveryEnabled;
        }
    }
}
