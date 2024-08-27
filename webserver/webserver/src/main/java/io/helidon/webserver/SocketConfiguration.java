/*
 * Copyright (c) 2017, 2024 Oracle and/or its affiliates.
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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;

import io.helidon.common.configurable.AllowList;
import io.helidon.config.Config;
import io.helidon.config.ConfigException;
import io.helidon.config.DeprecatedConfig;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * The SocketConfiguration configures a port to listen on and its associated server socket parameters.
 */
public interface SocketConfiguration {

    /**
     * The default backlog size to configure the server sockets with if no other value
     * is provided.
     */
    int DEFAULT_BACKLOG_SIZE = 1024;

    /**
     * Creates a builder of {@link SocketConfiguration} class.
     *
     * @return a builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create a default named configuration.
     *
     * @param name name of the socket
     * @return a new socket configuration with defaults
     */
    static SocketConfiguration create(String name) {
        return builder()
                .name(name)
                .build();
    }

    /**
     * Name of this socket.
     * Default to {@link io.helidon.webserver.WebServer#DEFAULT_SOCKET_NAME} for the main and
     * default server socket. All other sockets must be named.
     *
     * @return name of this socket
     */
    default String name() {
        return WebServer.DEFAULT_SOCKET_NAME;
    }

    /**
     * Returns a server port to listen on with the server socket. If port is
     * {@code 0} then any available ephemeral port will be used.
     *
     * @return the server port of the server socket
     */
    int port();

    /**
     * Returns local address where the server listens on with the server socket.
     * If {@code null} then listens an all local addresses.
     *
     * @return an address to bind with the server socket; {@code null} for all local addresses
     */
    InetAddress bindAddress();

    /**
     * Returns a maximum length of the queue of incoming connections on the server
     * socket.
     * <p>
     * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
     *
     * @return a maximum length of the queue of incoming connections
     */
    int backlog();

    /**
     * Returns a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
     *
     * @return a server socket timeout in milliseconds or {@code 0}
     */
    int timeoutMillis();

    /**
     * Returns proposed value of the TCP receive window that is advertised to the remote peer on the
     * server socket.
     * <p>
     * If {@code 0} then use implementation default.
     *
     * @return a buffer size in bytes of the server socket or {@code 0}
     */
    int receiveBufferSize();

    /**
     * Return a {@link WebServerTls} containing server TLS configuration. When empty {@link Optional} is returned
     * no TLS should be configured.
     *
     * @return web server tls configuration
     */
    Optional<WebServerTls> tls();

    /**
     * Returns a {@link SSLContext} to use with the server socket. If not {@code null} then
     * the server enforces an SSL communication.
     *
     * @deprecated use {@code tls().sslContext()} instead. This method will be removed at 3.0.0 version.
     * @return a SSL context to use
     */
    @Deprecated(since = "2.3.1", forRemoval = true)
    SSLContext ssl();

    /**
     * Returns the SSL protocols to enable, or {@code null} to enable the default
     * protocols.
     * @deprecated use {@code tls().enabledTlsProtocols()} instead. This method will be removed at 3.0.0 version.
     * @return the SSL protocols to enable
     */
    @Deprecated(since = "2.3.1", forRemoval = true)
    Set<String> enabledSslProtocols();

    /**
     * Return the allowed cipher suite of the TLS. If empty set is returned, the default cipher suite is used.
     *
     * @deprecated use {@code tls().cipherSuite()} instead. This method will be removed at 3.0.0 version.
     * @return the allowed cipher suite
     */
    @Deprecated(since = "2.3.1", forRemoval = true)
    Set<String> allowedCipherSuite();

    /**
     * Whether to require client authentication or not.
     *
     * @deprecated use {@code tls().clientAuth()} instead. This method will be removed at 3.0.0 version.
     * @return client authentication
     */
    @Deprecated(since = "2.3.1", forRemoval = true)
    ClientAuthentication clientAuth();

    /**
     * Whether this socket is enabled (and will be opened on server startup), or disabled
     * (and ignored on server startup).
     *
     * @return {@code true} for enabled socket, {@code false} for socket that should not be opened
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Maximal size of all headers combined.
     *
     * @return size in bytes
     */
    int maxHeaderSize();

    /**
     * Maximal length of the initial HTTP line.
     *
     * @return length
     */
    int maxInitialLineLength();

    /**
     * Maximal size of a single chunk of received data.
     *
     * @return chunk size
     */
    int maxChunkSize();

    /**
     * Whether to validate HTTP header names.
     * When set to {@code true}, we make sure the header name is a valid string
     *
     * @return {@code true} if headers should be validated
     */
    boolean validateHeaders();

    /**
     * Whether to allow negotiation for a gzip/deflate content encoding. Supporting
     * HTTP compression may interfere with application that use streaming and other
     * similar features. Thus, it defaults to {@code false}.
     *
     * @return compression flag
     */
    default boolean enableCompression() {
        return false;
    }

    /**
     * Maximum size allowed for an HTTP payload in a client request. A negative
     * value indicates that there is no maximum set.
     *
     * @return maximum payload size
     */
    default long maxPayloadSize() {
        return -1L;
    }

    /**
     * Maximum length of the response data sending buffer can keep without flushing.
     * Depends on `backpressure-policy` what happens if max buffer size is reached.
     *
     * @return maximum non-flushed data Netty can buffer until backpressure is applied
     */
    default long backpressureBufferSize() {
        return 5 * 1024 * 1024;
    }

    /**
     * Strategy for applying backpressure to the reactive stream of response data.
     * Switched default to {@link BackpressureStrategy#AUTO_FLUSH} since 3.1.1.
     *
     * @return strategy identifier for applying backpressure
     */
    default BackpressureStrategy backpressureStrategy() {
        return BackpressureStrategy.AUTO_FLUSH;
    }

    /**
     * When true WebServer answers with 100 continue immediately,
     * not waiting for user to actually request the data.
     * False is default value.
     *
     * @return strategy identifier for applying backpressure
     */
    default boolean continueImmediately() {
        return false;
    }

    /**
     * Initial size of the buffer used to parse HTTP line and headers.
     *
     * @return initial size of the buffer
     */
    int initialBufferSize();

    /**
     * Discovery types to identify requested URI.
     *
     * @return list with supported types, will always contain at least
     * {@link io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType#HOST}.
     */
    List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes();

    /**
     *
     * @return whether requested URI discovery is enabled.
     */
    boolean requestedUriDiscoveryEnabled();

    /**
     * The allow list for trusted proxies.
     *
     * @return allow list for trusted proxies
     */
    AllowList trustedProxies();

    /**
     * Maximum length of the content of an upgrade request.
     *
     * @return maximum length of the content of an upgrade request
     */
    default int maxUpgradeContentLength() {
        return 64 * 1024;
    }

    /**
     * Timeout seconds after which any idle connection will be automatically closed
     * by the server.
     *
     * @return idle connection timeout in seconds
     */
    default int connectionIdleTimeout() {
        return 0;
    }

    /**
     * Types of discovery of frontend uri. Defaults to {@link #HOST} when frontend uri discovery is disabled (uses only Host
     * header and information about current request to determine scheme, host, port, and path).
     * Defaults to {@link #FORWARDED} when discovery is enabled. Can be explicitly configured on socket configuration builder.
     */
    enum RequestedUriDiscoveryType {
        /**
         * The {@link io.helidon.common.http.Http.Header#FORWARDED} header is used to discover the original requested URI.
         */
        FORWARDED,
        /**
         * The
         * {@link io.helidon.common.http.Http.Header#X_FORWARDED_PROTO},
         * {@link io.helidon.common.http.Http.Header#X_FORWARDED_HOST},
         * {@link io.helidon.common.http.Http.Header#X_FORWARDED_PORT},
         * {@link io.helidon.common.http.Http.Header#X_FORWARDED_PREFIX}
         * headers are used to discover the original requested URI.
         */
        X_FORWARDED,
        /**
         * This is the default, only the {@link io.helidon.common.http.Http.Header#HOST} header is used to discover
         * requested URI.
         */
        HOST
    }

    /**
     * Socket configuration builder API, used by {@link io.helidon.webserver.SocketConfiguration.Builder}
     * to configure additional sockets, and by {@link io.helidon.webserver.WebServer.Builder} to
     * configure the default socket.
     *
     * @param <B> type of the subclass of this class to provide correct fluent API
     */
    @Configured
    interface SocketConfigurationBuilder<B extends SocketConfigurationBuilder<B>> {

        /**
         * Config key prefix for requested URI discovery settings.
         */
        String REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX = "requested-uri-discovery.";

        /**
         * Configures a server port to listen on with the server socket. If port is
         * {@code 0} then any available ephemeral port will be used.
         *
         * @param port the server port of the server socket
         * @return this builder
         */
        @ConfiguredOption("0")
        B port(int port);

        /**
         * Configures local address where the server listens on with the server socket.
         * If not configured, then listens an all local addresses.
         *
         * @param address an address to bind with the server socket
         * @return this builder
         * @throws java.lang.NullPointerException in case the bind address is null
         * @throws io.helidon.config.ConfigException in case the address provided is not a valid host address
         */
        @ConfiguredOption(deprecated = true)
        default B bindAddress(String address) {
            try {
                return bindAddress(InetAddress.getByName(address));
            } catch (UnknownHostException e) {
                throw new ConfigException("Illegal value of 'bind-address' configuration key. Expecting host or ip address!", e);
            }
        }

        /**
         * A helper method that just calls {@link #bindAddress(String)}.
         *
         * @param address host to listen on
         * @return this builder
         */
        @ConfiguredOption
        default B host(String address) {
            return bindAddress(address);
        }

        /**
         * Configures local address where the server listens on with the server socket.
         * If not configured, then listens an all local addresses.
         *
         * @param bindAddress an address to bind with the server socket
         * @return this builder
         * @throws java.lang.NullPointerException in case the bind address is null
         */
        B bindAddress(InetAddress bindAddress);

        /**
         * Configures a maximum length of the queue of incoming connections on the server
         * socket.
         * <p>
         * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
         *
         * @param backlog a maximum length of the queue of incoming connections
         * @return this builder
         */
        @ConfiguredOption("1024")
        B backlog(int backlog);

        /**
         * Configures a server socket timeout.
         *
         * @param amount an amount of time to configure the timeout, use {@code 0} for infinite timeout
         * @param unit time unit to use with the configured amount
         * @return this builder
         */
        @ConfiguredOption(key = "timeout-millis", type = Long.class, value = "0",
                          description = "Socket timeout in milliseconds")
        B timeout(long amount, TimeUnit unit);

        /**
         * Configures proposed value of the TCP receive window that is advertised to the remote peer on the
         * server socket.
         * <p>
         * If {@code 0} then use implementation default.
         *
         * @param receiveBufferSize a buffer size in bytes of the server socket or {@code 0}
         * @return this builder
         */
        @ConfiguredOption
        B receiveBufferSize(int receiveBufferSize);

        /**
         * Configures SSL for this socket. When configured, the server enforces SSL
         * configuration.
         * If this method is called, any other method except for {@link #tls(java.util.function.Supplier)}¨
         * and repeated invocation of this method would be ignored.
         * <p>
         * If this method is called again, the previous configuration would be ignored.
         *
         * @param webServerTls ssl configuration to use with this socket
         * @return this builder
         */
        @ConfiguredOption
        B tls(WebServerTls webServerTls);

        /**
         * Configures SSL for this socket. When configured, the server enforces SSL
         * configuration.
         *
         * @param tlsConfig supplier ssl configuration to use with this socket
         * @return this builder
         */
        default B tls(Supplier<WebServerTls> tlsConfig) {
            return tls(tlsConfig.get());
        }

        /**
         * Maximal number of bytes of all header values combined. When a bigger value is received, a
         * {@link io.helidon.common.http.Http.Status#BAD_REQUEST_400}
         * is returned.
         * <p>
         * Default is {@code 16384}
         *
         * @param size maximal number of bytes of combined header values
         * @return this builder
         */
        @ConfiguredOption("16384")
        B maxHeaderSize(int size);

        /**
         * Maximal number of characters in the initial HTTP line.
         * <p>
         * Default is {@code 4096}
         *
         * @param length maximal number of characters
         * @return this builder
         */
        @ConfiguredOption("4096")
        B maxInitialLineLength(int length);

        /**
         * Enable negotiation for gzip/deflate content encodings. Clients can
         * request compression using the "Accept-Encoding" header.
         * <p>
         * Default is {@code false}
         *
         * @param value compression flag
         * @return this builder
         */
        @ConfiguredOption("false")
        B enableCompression(boolean value);

        /**
         * Set a maximum payload size for a client request. Can prevent DoS
         * attacks.
         *
         * @param size maximum payload size
         * @return this builder
         */
        @ConfiguredOption
        B maxPayloadSize(long size);

        /**
         * Maximum length of the response data sending buffer can keep without flushing.
         * Depends on `backpressure-policy` what happens if max buffer size is reached.
         *
         * @param size maximum non-flushed data Netty can buffer until backpressure is applied
         * @return this builder
         */
        @ConfiguredOption
        B backpressureBufferSize(long size);

        /**
         * Sets a backpressure strategy for the server to apply against user provided response upstream.
         *
         * <ul>
         * <li>LINEAR - Data are requested one-by-one, in case buffer reaches watermark, no other data is requested.</li>
         * <li>AUTO_FLUSH - Data are requested one-by-one, in case buffer reaches watermark, no other data is requested.</li>
         * <li>PREFETCH - After first data chunk arrives, probable number of chunks needed to fill the buffer up to watermark is calculated and requested.</li>
         * <li>NONE - No backpressure is applied, Long.MAX_VALUE(unbounded) is requested from upstream.</li>
         * </ul>
         * @param backpressureStrategy One of NONE, PREFETCH, LINEAR or AUTO_FLUSH, default is AUTO_FLUSH
         * @return this builder
         */
        @ConfiguredOption("AUTO_FLUSH")
        B backpressureStrategy(BackpressureStrategy backpressureStrategy);

        /**
         * When true WebServer answers to expect continue with 100 continue immediately,
         * not waiting for user to actually request the data.
         * <p>
         * Default is {@code false}
         *
         * @param continueImmediately , answer with 100 continue immediately after expect continue, default is false
         * @return this builder
         */
        @ConfiguredOption("false")
        B continueImmediately(boolean continueImmediately);

        /**
         * Set a maximum length of the content of an upgrade request.
         * <p>
         * Default is {@code 64*1024}
         *
         * @param size Maximum length of the content of an upgrade request
         * @return this builder
         */
        @ConfiguredOption("65536")
        B maxUpgradeContentLength(int size);

        /**
         * Adds a type of front-end URI discovery Helidon should use for this socket. Adding a discovery type automatically
         * enables discovery for the socket.
         *
         * @param type type to add
         * @return updated builder
         */
        B addRequestedUriDiscoveryType(RequestedUriDiscoveryType type);

        /**
         * Assigns the front-end URI discovery type(s) this socket should use. This setting automatically enables
         * discovery for the socket.
         *
         * @param types {@link io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType} values to assign
         * @return updated builder
         * @see #addRequestedUriDiscoveryType(io.helidon.webserver.SocketConfiguration.RequestedUriDiscoveryType)
         * @see #requestedUriDiscoveryEnabled(boolean)
         * @see io.helidon.webserver.ServerRequest#requestedUri()
         */
        @ConfiguredOption(key = REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX + "types",
                          kind = ConfiguredOption.Kind.LIST,
                          value = "FORWARDED if discovery is enabled; none otherwise")
        B requestedUriDiscoveryTypes(List<RequestedUriDiscoveryType> types);

        /**
         * Sets whether requested URI discovery is enabled for the socket.
         *
         * @param enabled whether to enable discovery
         * @return updated builder
         * @see io.helidon.webserver.ServerRequest#requestedUri()
         */
        @ConfiguredOption(key = REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX + "enabled",
                          value = "true if '" + REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX + "types' or '"
                          + REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX + "trusted-proxies' is set; false otherwise")
        B requestedUriDiscoveryEnabled(boolean enabled);

        /**
         * Assigns the settings governing the acceptance and rejection of forwarded headers from incoming requests to this socket.
         * This setting automatically enables discovery for the socket.
         *
         * @param trustedProxies to apply to forwarded headers
         * @return updated builder
         * @see io.helidon.webserver.ServerRequest#requestedUri()
         */
        @ConfiguredOption(key = REQUESTED_URI_DISCOVERY_CONFIG_KEY_PREFIX + "trusted-proxies")
        B trustedProxies(AllowList trustedProxies);

        /**
         * Sets the number of seconds after which an idle connection will be automatically
         * closed by the server.
         *
         * @param seconds time in seconds
         * @return updated builder
         */
        B connectionIdleTimeout(int seconds);

        /**
         * Update this socket configuration from a {@link io.helidon.config.Config}.
         *
         * @param config configuration on the node of a socket
         * @return updated builder instance
         */
        @SuppressWarnings("unchecked")
        default B config(Config config) {
            config.get("port").asInt().ifPresent(this::port);
            config.get("bind-address").asString().ifPresent(this::host);
            config.get("backlog").asInt().ifPresent(this::backlog);
            config.get("max-header-size").asInt().ifPresent(this::maxHeaderSize);
            config.get("max-initial-line-length").asInt().ifPresent(this::maxInitialLineLength);
            config.get("max-payload-size").asLong().ifPresent(this::maxPayloadSize);

            DeprecatedConfig.get(config, "timeout-millis", "timeout")
                    .asInt()
                    .ifPresent(it -> this.timeout(it, TimeUnit.MILLISECONDS));
            DeprecatedConfig.get(config, "receive-buffer-size", "receive-buffer")
                    .asInt()
                    .ifPresent(this::receiveBufferSize);

            Optional<List<String>> enabledProtocols = DeprecatedConfig.get(config, "ssl.protocols", "ssl-protocols")
                    .asList(String.class)
                    .asOptional();

            // tls
            Config sslConfig = DeprecatedConfig.get(config, "tls", "ssl");
            if (sslConfig.exists()) {
                try {
                    WebServerTls.Builder builder = WebServerTls.builder();
                    enabledProtocols.ifPresent(builder::enabledProtocols);
                    builder.config(sslConfig);

                    this.tls(builder.build());
                } catch (IllegalStateException e) {
                    throw new ConfigException("Cannot load SSL configuration.", e);
                }
            }

            // compression
            config.get("enable-compression").asBoolean().ifPresent(this::enableCompression);
            config.get("backpressure-buffer-size").asLong().ifPresent(this::backpressureBufferSize);
            config.get("backpressure-strategy").as(BackpressureStrategy.class).ifPresent(this::backpressureStrategy);

            // URI discovery support
            config.get("requested-uri-discovery.enabled").as(Boolean.class).ifPresent(this::requestedUriDiscoveryEnabled);
            config.get("requested-uri-discovery.types").asList(RequestedUriDiscoveryType.class)
                    .ifPresent(this::requestedUriDiscoveryTypes);
            config.get("requested-uri-discovery.trusted-proxies").as(AllowList::create)
                    .ifPresent(this::trustedProxies);

            // idle connections
            config.get("connection-idle-timeout").asInt().ifPresent(this::connectionIdleTimeout);

            return (B) this;
        }
    }

    /**
     * The {@link io.helidon.webserver.SocketConfiguration} builder class.
     */
    @Configured
    final class Builder implements SocketConfigurationBuilder<Builder>, io.helidon.common.Builder<Builder, SocketConfiguration> {
        /**
         * @deprecated remove once WebServer.Builder.addSocket(name, socket) methods are removed
         */
        @Deprecated
        static final String UNCONFIGURED_NAME = "io.helidon.webserver.SocketConfiguration.UNCONFIGURED";
        private final WebServerTls.Builder tlsConfigBuilder = WebServerTls.builder();

        private static final Logger LOGGER = Logger.getLogger(SocketConfiguration.class.getName());

        private int port = 0;
        private InetAddress bindAddress = null;
        private int backlog = DEFAULT_BACKLOG_SIZE;
        private int timeoutMillis = 0;
        private int receiveBufferSize = 0;
        private WebServerTls webServerTls;
        // this is for backward compatibility, should be initialized to null once the
        // methods with `name` are removed from server builder (for adding sockets)
        private String name = UNCONFIGURED_NAME;
        private boolean enabled = true;
        // header size doubled comparing to default netty size
        private int maxHeaderSize = 16384;
        // these values are as defined in Netty implementation
        private int maxInitialLineLength = 4096;
        private int maxChunkSize = 8192;
        private boolean validateHeaders = true;
        private int initialBufferSize = 128;
        private boolean enableCompression = false;
        private long maxPayloadSize = -1;
        private BackpressureStrategy backpressureStrategy = BackpressureStrategy.AUTO_FLUSH;
        private boolean continueImmediately = false;
        private int maxUpgradeContentLength = 64 * 1024;
        private long maxBufferSize = 5 * 1024 * 1024;
        private final List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes = new ArrayList<>();
        private Boolean requestedUriDiscoveryEnabled;
        private AllowList trustedProxies;
        private int connectionIdleTimeout;

        private Builder() {
        }

        @Override
        public SocketConfiguration build() {
            if (null == webServerTls) {
                webServerTls = tlsConfigBuilder.build();
            }

            if (null == name) {
                throw new ConfigException("Socket name must be configured for each socket");
            }
            prepareAndCheckRequestedUriSettings();

            return new ServerBasicConfig.SocketConfig(this);
        }

        @Override
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        @Override
        public Builder bindAddress(InetAddress bindAddress) {
            this.bindAddress = bindAddress;
            return this;
        }

        /**
         * Configures a maximum length of the queue of incoming connections on the server
         * socket.
         * <p>
         * Default value is {@link #DEFAULT_BACKLOG_SIZE}.
         *
         * @param backlog a maximum length of the queue of incoming connections
         * @return this builder
         */
        public Builder backlog(int backlog) {
            this.backlog = backlog;
            return this;
        }

        /**
         * Configures a server socket timeout in milliseconds or {@code 0} for an infinite timeout.
         *
         * @param timeoutMillis a server socket timeout in milliseconds or {@code 0}
         * @return this builder
         *
         * @deprecated since 2.0.0 please use {@link #timeout(long, java.util.concurrent.TimeUnit)} instead
         */
        @Deprecated
        public Builder timeoutMillis(int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }

        /**
         * Configures proposed value of the TCP receive window that is advertised to the remote peer on the
         * server socket.
         * <p>
         * If {@code 0} then use implementation default.
         *
         * @param receiveBufferSize a buffer size in bytes of the server socket or {@code 0}
         * @return this builder
         */
        @Override
        public Builder receiveBufferSize(int receiveBufferSize) {
            this.receiveBufferSize = receiveBufferSize;
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param sslContext a SSL context to use
         * @return this builder
         *
         * @deprecated since 2.0.0, please use {@link #tls(WebServerTls)} instead
         */
        @Deprecated
        public Builder ssl(SSLContext sslContext) {
            if (null != sslContext) {
                this.tlsConfigBuilder.sslContext(sslContext);
            }
            return this;
        }

        /**
         * Configures a {@link SSLContext} to use with the server socket. If not {@code null} then
         * the server enforces an SSL communication.
         *
         * @param sslContextBuilder a SSL context builder to use; will be built as a first step of this
         *                          method execution
         * @return this builder
         * @deprecated since 2.0.0, please use {@link #tls(Supplier)} instead
         */
        @Deprecated
        public Builder ssl(Supplier<? extends SSLContext> sslContextBuilder) {
            return ssl(sslContextBuilder != null ? sslContextBuilder.get() : null);
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} enables the
         * default protocols
         * @return this builder
         *
         * @deprecated since 2.0.0, please use {@link WebServerTls.Builder#enabledProtocols(String...)}
         *              instead
         */
        @Deprecated
        public Builder enabledSSlProtocols(String... protocols) {
            if (null == protocols) {
                enabledSSlProtocols(List.of());
            } else {
                enabledSSlProtocols(Arrays.asList(protocols));
            }
            return this;
        }

        /**
         * Configures the SSL protocols to enable with the server socket.
         * @param protocols protocols to enable, if {@code null} or empty enables
         *  the default protocols
         * @return this builder
         */
        @Deprecated
        public Builder enabledSSlProtocols(List<String> protocols) {
            if (null == protocols) {
                this.tlsConfigBuilder.enabledProtocols(List.of());
            } else {
                this.tlsConfigBuilder.enabledProtocols(protocols);
            }
            return this;
        }

        @Override
        public Builder timeout(long amount, TimeUnit unit) {
            long timeout = unit.toMillis(amount);
            if (timeout > Integer.MAX_VALUE) {
                this.timeoutMillis = 0;
            } else {
                this.timeoutMillis = (int) timeout;
            }
            return this;
        }

        @Override
        public Builder tls(WebServerTls webServerTls) {
            this.webServerTls = webServerTls;
            return this;
        }

        @Override
        public Builder maxHeaderSize(int size) {
            this.maxHeaderSize = size;
            return this;
        }

        @Override
        public Builder maxInitialLineLength(int length) {
            this.maxInitialLineLength = length;
            return this;
        }

        @Override
        public Builder maxPayloadSize(long size) {
            this.maxPayloadSize = size;
            return this;
        }

        @Override
        public Builder backpressureBufferSize(long size) {
            this.maxBufferSize = size;
            return this;
        }

        @Override
        public Builder backpressureStrategy(BackpressureStrategy backpressureStrategy) {
            this.backpressureStrategy = backpressureStrategy;
            return this;
        }

        @Override
        public Builder continueImmediately(boolean continueImmediately) {
            this.continueImmediately = continueImmediately;
            return this;
        }

        @Override
        public Builder maxUpgradeContentLength(int size) {
            this.maxUpgradeContentLength = size;
            return this;
        }

        /**
         * Configure a socket name, to bind named routings to.
         *
         * @param name name of the socket
         * @return updated builder instance
         */
        @ConfiguredOption(required = true)
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Set this socket builder to enabled or disabled.
         *
         * @param enabled when set to {@code false}, the socket is not going to be opened by the server
         * @return updated builder instance
         */
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Configure maximal size of a chunk to be read from incoming requests.
         * Defaults to {@code 8192}.
         *
         * @param size maximal chunk size
         * @return updated builder instance
         */
        public Builder maxChunkSize(int size) {
            this.maxChunkSize = size;
            return this;
        }

        /**
         * Configure whether to validate header names.
         * Defaults to {@code true} to make sure header names are valid strings.
         *
         * @param validate set to {@code false} to ignore header validation
         * @return updated builder instance
         */
        public Builder validateHeaders(boolean validate) {
            this.validateHeaders = validate;
            return this;
        }

        /**
         * Configure initial size of the buffer used to parse HTTP line and headers.
         * Defaults to {@code 128}.
         *
         * @param size initial buffer size
         * @return updated builder instance
         */
        public Builder initialBufferSize(int size) {
            this.initialBufferSize = size;
            return this;
        }

        /**
         * Configure whether to enable content negotiation for compression.
         *
         * @param value compression flag
         * @return updated builder instance
         */
        @Override
        public Builder enableCompression(boolean value) {
            this.enableCompression = value;
            return this;
        }

        /**
         * Configure the trusted proxy settings.
         *
         * @param trustedProxies prescribing proxies to be trusted
         * @return updated builder instance
         */
        @Override
        public Builder trustedProxies(AllowList trustedProxies) {
            this.trustedProxies = trustedProxies;
            this.requestedUriDiscoveryEnabled = true;
            return this;
        }

        @Override
        public Builder config(Config config) {
            SocketConfigurationBuilder.super.config(config);

            config.get("name").asString().ifPresent(this::name);
            config.get("enabled").asBoolean().ifPresent(this::enabled);
            config.get("max-chunk-size").asInt().ifPresent(this::maxChunkSize);
            config.get("validate-headers").asBoolean().ifPresent(this::validateHeaders);
            config.get("initial-buffer-size").asInt().ifPresent(this::initialBufferSize);
            config.get("enable-compression").asBoolean().ifPresent(this::enableCompression);
            config.get("backpressure-buffer-size").asLong().ifPresent(this::backpressureBufferSize);
            config.get("backpressure-strategy").as(BackpressureStrategy.class).ifPresent(this::backpressureStrategy);
            config.get("connection-idle-timeout").asInt().ifPresent(this::connectionIdleTimeout);

            return this;
        }

        @Override
        public Builder addRequestedUriDiscoveryType(RequestedUriDiscoveryType type) {
            this.requestedUriDiscoveryTypes.add(type);
            return this;
        }

        @Override
        public Builder requestedUriDiscoveryTypes(List<RequestedUriDiscoveryType> types) {
            requestedUriDiscoveryTypes.clear();
            requestedUriDiscoveryTypes.addAll(types);
            return this;
        }

        @Override
        public Builder requestedUriDiscoveryEnabled(boolean enabled) {
            this.requestedUriDiscoveryEnabled = enabled;
            return this;
        }

        @Override
        public Builder connectionIdleTimeout(int seconds) {
            this.connectionIdleTimeout = seconds;
            return this;
        }

        int port() {
            return port;
        }

        Optional<InetAddress> bindAddress() {
            return Optional.ofNullable(bindAddress);
        }

        int backlog() {
            return backlog;
        }

        int timeoutMillis() {
            return timeoutMillis;
        }

        int receiveBufferSize() {
            return receiveBufferSize;
        }

        WebServerTls tlsConfig() {
            return webServerTls;
        }

        String name() {
            return name;
        }

        boolean enabled() {
            return enabled;
        }

        int maxHeaderSize() {
            return maxHeaderSize;
        }

        int maxInitialLineLength() {
            return maxInitialLineLength;
        }

        int maxChunkSize() {
            return maxChunkSize;
        }

        boolean validateHeaders() {
            return validateHeaders;
        }

        int initialBufferSize() {
            return initialBufferSize;
        }

        boolean enableCompression() {
            return enableCompression;
        }

        long maxPayloadSize() {
            return maxPayloadSize;
        }

        long backpressureBufferSize() {
            return maxBufferSize;
        }

        BackpressureStrategy backpressureStrategy() {
            return backpressureStrategy;
        }

        boolean continueImmediately() {
            return continueImmediately;
        }

        int maxUpgradeContentLength() {
            return maxUpgradeContentLength;
        }

        List<RequestedUriDiscoveryType> requestedUriDiscoveryTypes() {
            if (requestedUriDiscoveryEnabled && !requestedUriDiscoveryTypes.isEmpty()) {
                return requestedUriDiscoveryTypes;
            }
            return List.of(RequestedUriDiscoveryType.HOST);
        }

        AllowList trustedProxies() {
            return trustedProxies;
        }

        boolean requestedUriDiscoveryEnabled() {
            return requestedUriDiscoveryEnabled;
        }

        int connectionIdleTimeout() {
            return connectionIdleTimeout;
        }

        /**
         * Checks validity of requested URI settings and supplies defaults for omitted settings.
         * <p>The behavior of `requested-uri-discovery` settings can be summarized as follows:
         *     <ul>
         *     <li>The `requested-uri-discovery` settings are optional.</li>
         *     <li>If `requested-uri-discovery` is absent or is present with `enabled` explicitly set to `false`, Helidon
         *     ignores any {@code Forwarded} or {@code X-Forwarded-*} headers and adopts the
         *     {@code HOST} discovery type. That is, Helidon uses the {@code Host} header for the host
         *     and the request's scheme and port.</li>
         *     <li>If `requested-uri-discovery` is present and enabled, either because {@code enabled} is set to {@code true}
         *     or {@code types} or {@code trusted-proxies} has been set, then Helidon performs a simple validity
         *     check before adopting the selected discovery behavior: If {@code types} is specified and includes
         *     either {@code FORWARDED} or {@code X_FORWARDED} then {@code trusted-proxies} must also be set to at least
         *     one value. Put another way, if requested URI discovery is enabled then {@code trusted-proxies} can be unspecified
         *     only if {@code types} contains only {@code HOST}.</li>
         *     </ul>
         * </p>
         */
        private void prepareAndCheckRequestedUriSettings() {
            boolean isDiscoveryEnabledDefaulted = false;
            if (requestedUriDiscoveryEnabled == null) {
                requestedUriDiscoveryEnabled = !requestedUriDiscoveryTypes.isEmpty() || trustedProxies != null;
                isDiscoveryEnabledDefaulted = true;
            }

            boolean areDiscoveryTypesDefaulted = false;

            if (requestedUriDiscoveryEnabled) {
                // Configure a default type if discovery is enabled and no explicit types are configured.
                if (this.requestedUriDiscoveryTypes.isEmpty()) {
                    areDiscoveryTypesDefaulted = true;
                    this.requestedUriDiscoveryTypes.add(RequestedUriDiscoveryType.FORWARDED);
                }

                // Require _some_ settings for trusted proxies (except for HOST discovery) so the socket does not start unsafely
                // by accident. The user _can_ set allow.all to run the socket unsafely but at least that way it was
                // an explicit choice.
                if (trustedProxies == null && !isDiscoveryTypesOnlyHost()) {
                    throw new UnsafeRequestedUriSettingsException(this, areDiscoveryTypesDefaulted);
                }
            } else {
                // Discovery is disabled so ignore any explicit settings of discovery type and use HOST discovery.
                if (!requestedUriDiscoveryTypes.isEmpty() && !isDiscoveryTypesOnlyHost()) {
                    LOGGER.log(Level.INFO, """
                            Ignoring explicit settings of requested-uri-discovery types and trusted-proxies because
                            requested-uri-discovery.enabled {0} to false
                            """, isDiscoveryEnabledDefaulted ? " defaulted" : "was set");
                }
                requestedUriDiscoveryTypes.clear();
                requestedUriDiscoveryTypes.add(RequestedUriDiscoveryType.HOST);
            }
            if (trustedProxies == null) {
                trustedProxies = AllowList.builder()
                        .addDenied(s -> true)
                        .build();
            }
        }

        private boolean isDiscoveryTypesOnlyHost() {
            return requestedUriDiscoveryTypes.size() == 1
                    && requestedUriDiscoveryTypes.contains(RequestedUriDiscoveryType.HOST);
        }
    }
}
