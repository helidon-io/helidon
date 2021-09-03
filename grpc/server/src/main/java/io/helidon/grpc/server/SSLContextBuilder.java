/*
 * Copyright (c) 2019, 2020 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSessionContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.Builder;
import io.helidon.common.pki.KeyConfig;
import io.helidon.config.Config;

/**
 * Builder for configuring a new SslContext for creation.
 */
public final class SSLContextBuilder implements Builder<SSLContext> {

    private static final String PROTOCOL = "TLS";
    private static final Random RANDOM = new Random();

    private KeyConfig privateKeyConfig;
    private KeyConfig trustConfig;
    private long sessionCacheSize;
    private long sessionTimeout;

    private SSLContextBuilder() {
    }

    /**
     * Creates a builder of the {@link javax.net.ssl.SSLContext}.
     *
     * @param privateKeyConfig the required private key configuration parameter
     * @return this builder
     */
    public static SSLContextBuilder create(KeyConfig privateKeyConfig) {
        return new SSLContextBuilder().privateKeyConfig(privateKeyConfig);
    }

    /**
     * Creates {@link javax.net.ssl.SSLContext} from the provided configuration.
     *
     * @param sslConfig the ssl configuration
     * @return a built {@link javax.net.ssl.SSLContext}
     * @throws IllegalStateException in case of a problem; will wrap either an instance of {@link java.io.IOException} or
     *                               a {@link java.security.GeneralSecurityException}
     */
    public static SSLContext create(Config sslConfig) {
        return new SSLContextBuilder().privateKeyConfig(KeyConfig.create(sslConfig.get("private-key")))
                                      .sessionCacheSize(sslConfig.get("session-cache-size").asInt().orElse(0))
                                      .sessionTimeout(sslConfig.get("session-timeout").asInt().orElse(0))
                                      .trustConfig(KeyConfig.create(sslConfig.get("trust")))
                                      .build();
    }

    private SSLContextBuilder privateKeyConfig(KeyConfig privateKeyConfig) {
        this.privateKeyConfig = privateKeyConfig;
        return this;
    }

    /**
     * Set the trust key configuration to be used to validate certificates.
     *
     * @param trustConfig the trust configuration
     * @return an updated builder
     */
    public SSLContextBuilder trustConfig(KeyConfig trustConfig) {
        this.trustConfig = trustConfig;
        return this;
    }

    /**
     * Set the size of the cache used for storing SSL session objects. {@code 0} to use the
     * default value.
     *
     * @param sessionCacheSize the session cache size
     * @return an updated builder
     */
    public SSLContextBuilder sessionCacheSize(long sessionCacheSize) {
        this.sessionCacheSize = sessionCacheSize;
        return this;
    }

    /**
     * Set the timeout for the cached SSL session objects, in seconds. {@code 0} to use the
     * default value.
     *
     * @param sessionTimeout the session timeout
     * @return an updated builder
     */
    public SSLContextBuilder sessionTimeout(long sessionTimeout) {
        this.sessionTimeout = sessionTimeout;
        return this;
    }

    /**
     * Create new {@code {@link javax.net.ssl.SSLContext}} instance with configured settings.
     *
     * @return the SSL Context built instance
     * @throws IllegalStateException in case of a problem; will wrap either an instance of {@link java.io.IOException} or
     *                               a {@link java.security.GeneralSecurityException}
     */
    public SSLContext build() {
        Objects.requireNonNull(privateKeyConfig, "The private key config must be set!");

        try {
            return newSSLContext(privateKeyConfig, trustConfig, sessionCacheSize, sessionTimeout);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Building of the SSLContext of unsuccessful!", e);
        }
    }

    private static SSLContext newSSLContext(KeyConfig privateKeyConfig,
                                            KeyConfig trustConfig,
                                            long sessionCacheSize,
                                            long sessionTimeout)
            throws IOException, GeneralSecurityException {
        KeyManagerFactory kmf = buildKmf(privateKeyConfig);
        TrustManagerFactory tmf = buildTmf(trustConfig);

        // Initialize the SSLContext to work with our key managers.
        SSLContext ctx = SSLContext.getInstance(PROTOCOL);
        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        SSLSessionContext sessCtx = ctx.getServerSessionContext();
        if (sessionCacheSize > 0) {
            sessCtx.setSessionCacheSize((int) Math.min(sessionCacheSize, Integer.MAX_VALUE));
        }
        if (sessionTimeout > 0) {
            sessCtx.setSessionTimeout((int) Math.min(sessionTimeout, Integer.MAX_VALUE));
        }
        return ctx;
    }

    private static KeyManagerFactory buildKmf(KeyConfig privateKeyConfig) throws IOException, GeneralSecurityException {
        String algorithm = Security.getProperty("ssl.KeyManagerFactory.algorithm");
        if (algorithm == null) {
            algorithm = "SunX509";
        }

        byte[] passwordBytes = new byte[64];
        RANDOM.nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setKeyEntry("key",
                       privateKeyConfig.privateKey().orElseThrow(() -> new RuntimeException("Private key not available")),
                       password,
                       privateKeyConfig.certChain().toArray(new Certificate[0]));

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(algorithm);
        kmf.init(ks, password);

        return kmf;
    }

    private static TrustManagerFactory buildTmf(KeyConfig trustConfig)
            throws IOException, GeneralSecurityException {
        List<X509Certificate> certs;

        if (trustConfig == null) {
            certs = List.of();
        } else {
            certs = trustConfig.certs();
        }

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);

        int i = 1;
        for (X509Certificate cert : certs) {
            ks.setCertificateEntry(String.valueOf(i), cert);
            i++;
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf;
    }
}
