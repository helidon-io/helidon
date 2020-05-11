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

import java.io.IOException;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;

import io.helidon.common.Builder;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

/**
 * Builder for configuring a new SslContext for creation.
 *
 * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
 *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
 *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
 */
@Deprecated
public final class SSLContextBuilder implements Builder<SSLContext> {

    private final TlsConfig.Builder tlsConfig = TlsConfig.builder();

    private SSLContextBuilder() {
    }

    /**
     * Creates a builder of the {@link SSLContext}.
     *
     * @param privateKeyConfig the required private key configuration parameter
     * @return this builder
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public static SSLContextBuilder create(KeyConfig privateKeyConfig) {
        return new SSLContextBuilder().privateKeyConfig(privateKeyConfig);
    }

    /**
     * Creates {@link SSLContext} from the provided configuration.
     *
     * @param sslConfig the ssl configuration
     * @return a built {@link SSLContext}
     * @throws IllegalStateException in case of a problem; will wrap either an instance of {@link IOException} or
     *                               a {@link GeneralSecurityException}
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public static SSLContext create(Config sslConfig) {
        return new SSLContextBuilder().privateKeyConfig(KeyConfig.create(sslConfig.get("private-key")))
                .sessionCacheSize(sslConfig.get("session-cache-size").asInt().orElse(0))
                .sessionTimeout(sslConfig.get("session-timeout").asInt().orElse(0))
                .trustConfig(KeyConfig.create(sslConfig.get("trust")))
                .build();
    }

    private SSLContextBuilder privateKeyConfig(KeyConfig privateKeyConfig) {
        tlsConfig.privateKey(privateKeyConfig);
        return this;
    }

    /**
     * Set the trust key configuration to be used to validate certificates.
     *
     * @param trustConfig the trust configuration
     * @return an updated builder
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public SSLContextBuilder trustConfig(KeyConfig trustConfig) {
        tlsConfig.trust(trustConfig);
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     *
     * @param sessionCacheSize the session cache size
     * @return an updated builder
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public SSLContextBuilder sessionCacheSize(long sessionCacheSize) {
        tlsConfig.sessionCacheSize(sessionCacheSize);
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     *
     * @param sessionTimeout the session timeout
     * @return an updated builder
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public SSLContextBuilder sessionTimeout(long sessionTimeout) {
        tlsConfig.sessionTimeoutSeconds(sessionTimeout);
        return this;
    }

    /**
     * Create new {@code {@link SSLContext}} instance with configured settings.
     *
     * @return the SSL Context built instance
     * @throws IllegalStateException in case of a problem; will wrap either an instance of {@link IOException} or
     *                               a {@link GeneralSecurityException}
     *
     * @deprecated since 2.0.0, please use {@link TlsConfig#builder()} instead,
     *  then configure it with {@link io.helidon.webserver.WebServer.Builder#tls(TlsConfig)}
     *  or {@link io.helidon.webserver.SocketConfiguration.SocketConfigurationBuilder#tls(TlsConfig)}
     */
    @Deprecated
    public SSLContext build() {
        tlsConfig.enabled(true);

        return tlsConfig.build()
                .sslContext();
    }
}
