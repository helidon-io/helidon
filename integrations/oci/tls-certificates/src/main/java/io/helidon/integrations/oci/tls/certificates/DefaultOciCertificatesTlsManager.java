/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.ConfiguredTlsManager;
import io.helidon.common.tls.TlsConfig;
import io.helidon.config.Config;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

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

    private Supplier<OciPrivateKeyDownloader> pkDownloader;
    private Supplier<OciCertificatesDownloader> certDownloader;
    private TlsConfig tlsConfig;

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg) {
        this(cfg, "@default", null);
    }

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg,
                                     String name,
                                     io.helidon.common.config.Config config) {
        super(name, TYPE);
        this.cfg = Objects.requireNonNull(cfg);

        // if config changes then will do a reload
        if (config instanceof Config watchableConfig) {
            watchableConfig.onChange(this::config);
        }
    }

    @Override // TlsManager
    public void init(TlsConfig tls) {
        this.tlsConfig = tls;
        ServiceRegistry registry = GlobalServiceRegistry.registry();
        this.pkDownloader = registry.supply(OciPrivateKeyDownloader.class);
        this.certDownloader = registry.supply(OciCertificatesDownloader.class);
        ScheduledExecutorService asyncExecutor = Executors.newSingleThreadScheduledExecutor();

        // the initial loading of the tls
        loadContext(true);

        // now schedule for reload checking
        String taskIntervalDescription =
                io.helidon.scheduling.Scheduling.cron()
                        .executor(asyncExecutor)
                        .expression(cfg.schedule())
                        .task(inv -> maybeReload())
                        .build()
                        .description();

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG,
                    "Scheduled: " + taskIntervalDescription);
        }
    }

    @Override // RuntimeType
    public OciCertificatesTlsManagerConfig prototype() {
        return cfg;
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
    void config(io.helidon.common.config.Config config) {
        Objects.requireNonNull(config);
        maybeReload();
    }

    /**
     * Will download new certificates, and if those are determined to be changed will affect the reload of the new key and trust
     * managers.
     *
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
                initializeTmf(tmf, keyStore, tlsConfig);
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
                initSslContext(tlsConfig, secureRandom, kmf.getKeyManagers(), tmf.getTrustManagers());
            } else {
                reload(keyManager, trustManager);
            }

            return true;
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Error while loading context from OCI", e);
        }
    }

}
