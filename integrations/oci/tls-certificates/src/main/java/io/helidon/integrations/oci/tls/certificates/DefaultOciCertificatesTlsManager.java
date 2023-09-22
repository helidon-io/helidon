/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.tls.certificates;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.faulttolerance.Async;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;
import io.helidon.webserver.ConfiguredTlsManager;
import io.helidon.webserver.WebServerTls;

import jakarta.inject.Provider;

import static io.helidon.integrations.oci.tls.certificates.InjectionServices.Services;
import static io.helidon.integrations.oci.tls.certificates.InjectionServices.realizedServices;

/**
 * The default implementation (service loader and provider-driven) implementation of {@link OciCertificatesTlsManager}.
 *
 * @see DefaultOciCertificatesTlsManagerProvider
 */
class DefaultOciCertificatesTlsManager extends ConfiguredTlsManager implements OciCertificatesTlsManager {
    static final String TYPE = "oci-certificates-tls-manager";
    private static final System.Logger LOGGER = System.getLogger(DefaultOciCertificatesTlsManager.class.getName());

    private final OciCertificatesTlsManagerConfig cfg;
    private final AtomicReference<String> lastVersionDownloaded = new AtomicReference<>("");

    // these will only be non-null when enabled
    private Provider<OciPrivateKeyDownloader> pkDownloader;
    private Provider<OciCertificatesDownloader> certDownloader;
    private ScheduledExecutorService asyncExecutor;
    private Async async;
    private WebServerTls tlsConfig;

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg) {
        this(cfg, "@default", null);
    }

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg,
                                     String name,
                                     io.helidon.config.Config config) {
        super(name, TYPE);
        this.cfg = Objects.requireNonNull(cfg);

        // if config changes then will do a reload
        if (config != null) {
            config.onChange(this::config);
        }
    }

    @Override // TlsManager
    public void init(WebServerTls tls) {
        this.tlsConfig = tls;
        Services services = realizedServices();
        this.pkDownloader = services.lookupFirst(OciPrivateKeyDownloader.class);
        this.certDownloader = services.lookupFirst(OciCertificatesDownloader.class);
        this.asyncExecutor = Executors.newSingleThreadScheduledExecutor();
        this.async = Async.builder().executor(asyncExecutor).build();

        // the initial loading of the tls
        loadContext(true);

        // register for any available graceful shutdown events
        Optional<Provider<LifecycleHook>> shutdownHook = services.lookupFirst(LifecycleHook.class, false);
        shutdownHook.ifPresent(sp -> sp.get().registerShutdownConsumer(this::shutdown));

        // now schedule for reload checking
        String taskIntervalDescription =
                io.helidon.scheduling.Scheduling.cronBuilder()
                        .executor(asyncExecutor)
                        .expression(cfg.schedule())
                        .task(inv -> maybeReload())
                        .build()
                        .description();
        LOGGER.log(System.Logger.Level.DEBUG, () ->
                OciCertificatesTlsManagerConfig.class.getSimpleName() + " scheduled: " + taskIntervalDescription);
    }

    private void shutdown(Object event) {
        try {
            LOGGER.log(System.Logger.Level.DEBUG, "Shutting down");
            asyncExecutor.shutdownNow();
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Shut down failed", e);
        }
    }

    // ConfiguredTlsManager
    private void maybeReload() {
        if (loadContext(false)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Certificates were downloaded and dynamically updated");
        }
    }

    /**
     * Sets the backing (possibly updated) configuration for this manager. This will trigger a reload.
     *
     * @param config the new config
     */
    void config(io.helidon.config.Config config) {
        Objects.requireNonNull(config);
        maybeReload();
    }

    /**
     * Will download new certificates, and if those are determined to be changed will affect the reload of the new key and trust
     * managers.
     *
     * @param initialLoad flag indicating whether this is the initial loading of config
     * @return true if a reload occurred
     */
    boolean loadContext(boolean initialLoad) {
        try {
            // download all of our security collateral from OCI
            OciCertificatesDownloader cd = certDownloader.get();
            OciCertificatesDownloader.Certificates certificates = cd.loadCertificates(cfg.certOcid());
            if (lastVersionDownloaded.get().equals(certificates.version())) {
                assert (!initialLoad);
                return false;
            }

            // reset start time for the next update phase
            Certificate ca = cd.loadCACertificate(cfg.caOcid());

            OciPrivateKeyDownloader pd = pkDownloader.get();
            PrivateKey key = pd.loadKey(cfg.keyOcid(), cfg.vaultCryptoEndpoint());
            SecureRandom secureRandom = secureRandom(tlsConfig);
            KeyManagerFactory kmf = buildKmf(tlsConfig, secureRandom, key, certificates.certificates());

            TrustManagerFactory tmf;
            if (tlsConfig.trustAll()) {
                tmf = trustAllTmf();
            } else {
                tmf = createTmf(tlsConfig);
                KeyStore keyStore = internalKeystore(tlsConfig);
                keyStore.setCertificateEntry("trust-ca", ca);
                tmf.init(keyStore);
            }

            Optional<X509KeyManager> keyManager = Arrays.stream(kmf.getKeyManagers())
                    .filter(m -> m instanceof X509KeyManager)
                    .map(X509KeyManager.class::cast)
                    .findFirst();
            if (keyManager.isEmpty()) {
                throw new RuntimeException("Unable to find X.509 key manager in download: " + cfg.certOcid());
            }

            Optional<X509TrustManager> trustManager = Arrays.stream(tmf.getTrustManagers())
                    .filter(m -> m instanceof X509TrustManager)
                    .map(X509TrustManager.class::cast)
                    .findFirst();
            if (trustManager.isEmpty()) {
                throw new RuntimeException("Unable to find X.509 trust manager in download: " + cfg.certOcid());
            }

            if (initialLoad) {
                initSslContext(tlsConfig, kmf.getKeyManagers(), tmf.getTrustManagers());
            } else {
                reload(tlsConfig, kmf.getKeyManagers(), tmf.getTrustManagers());
            }

            return true;
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Error while loading context from OCI", e);
        }
    }

    private KeyManagerFactory buildKmf(WebServerTls target,
                                       SecureRandom secureRandom,
                                       PrivateKey privateKey,
                                       Certificate[] certificates) {
        byte[] passwordBytes = new byte[64];
        secureRandom.nextBytes(passwordBytes);
        char[] password = Base64.getEncoder().encodeToString(passwordBytes).toCharArray();

        try {
            KeyStore ks = internalKeystore(target);
            ks.setKeyEntry("key",
                           privateKey,
                           password,
                           certificates);

            KeyManagerFactory kmf = kmf(target);
            kmf.init(ks, password);
            return kmf;
        } catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Invalid configuration for key management factory, cannot create factory", e);
        }
    }

    KeyManagerFactory kmf(WebServerTls tlsConfig) {
        try {
            String algorithm = KeyManagerFactory.getDefaultAlgorithm();
            return KeyManagerFactory.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Invalid configuration of key manager factory.", e);
        }
    }

    private KeyStore internalKeystore(WebServerTls tlsConfig) {
        try {
            String type = KeyStore.getDefaultType();
            KeyStore ks = KeyStore.getInstance(type);
            ks.load(null, null);
            return ks;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException("Invalid configuration of internal keystores", e);
        }
    }

}
