/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.ConfiguredTlsManager;
import io.helidon.common.tls.TlsConfig;
import io.helidon.config.Config;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.scheduling.Cron;
import io.helidon.service.registry.GlobalServiceRegistry;
import io.helidon.service.registry.ServiceRegistry;

/**
 * Default implementation of {@link OciCertificateBundleTlsManager}.
 *
 * @see DefaultOciCertificateBundleTlsManagerProvider
 */
class DefaultOciCertificateBundleTlsManager extends ConfiguredTlsManager
        implements OciCertificateBundleTlsManager {
    static final String TYPE = "oci-certificate-bundle-tls-manager";
    private static final System.Logger LOGGER =
            System.getLogger(DefaultOciCertificateBundleTlsManager.class.getName());

    private final OciCertificateBundleTlsManagerConfig cfg;
    private final AtomicReference<ReloadToken> installedMaterial = new AtomicReference<>();
    private final ReentrantLock reloadLock = new ReentrantLock();

    private Supplier<OciCertificatesDownloader> certDownloader;
    private TlsConfig tlsConfig;

    DefaultOciCertificateBundleTlsManager(OciCertificateBundleTlsManagerConfig cfg) {
        this(cfg, "@default", null);
    }

    DefaultOciCertificateBundleTlsManager(OciCertificateBundleTlsManagerConfig cfg,
                                          String name,
                                          io.helidon.common.config.Config config) {
        super(name, TYPE);
        this.cfg = Objects.requireNonNull(cfg);

        if (config instanceof Config watchableConfig) {
            watchableConfig.onChange(this::config);
        }
    }

    @Override
    public void init(TlsConfig tls) {
        this.tlsConfig = tls;
        ServiceRegistry registry = GlobalServiceRegistry.registry();
        this.certDownloader = registry.supply(OciCertificatesDownloader.class);
        ScheduledExecutorService asyncExecutor = Executors.newSingleThreadScheduledExecutor();

        loadContext(true);

        String taskIntervalDescription =
                Cron.builder()
                        .executor(asyncExecutor)
                        .expression(cfg.schedule())
                        .concurrentExecution(false)
                        .task(inv -> maybeReload())
                        .build()
                        .description();

        if (LOGGER.isLoggable(System.Logger.Level.DEBUG)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Scheduled: " + taskIntervalDescription);
        }
    }

    @Override
    public OciCertificateBundleTlsManagerConfig prototype() {
        return cfg;
    }

    private void maybeReload() {
        try {
            if (loadContext(false)) {
                LOGGER.log(System.Logger.Level.DEBUG, "Certificates were downloaded and dynamically updated");
            }
        } catch (RuntimeException e) {
            String failureCategory;
            if (e instanceof UnsupportedOperationException) {
                failureCategory = "unsupported-operation";
            } else if (e instanceof IllegalArgumentException) {
                failureCategory = "invalid-tls-material";
            } else if (e instanceof IllegalStateException) {
                failureCategory = "oci-download-or-tls-state";
            } else {
                failureCategory = "runtime-failure";
            }
            LOGGER.log(System.Logger.Level.WARNING,
                       "Failed to refresh OCI certificate " + cfg.certOcid()
                               + " (failure category: " + failureCategory + ")"
                               + "; the previously installed TLS identity remains active and the refresh will be retried");
        }
    }

    /**
     * Triggers a reload after a backing configuration change.
     *
     * @param config the updated config
     */
    void config(io.helidon.common.config.Config config) {
        Objects.requireNonNull(config);
        if (loadContext(false)) {
            LOGGER.log(System.Logger.Level.DEBUG, "Certificates were downloaded and dynamically updated");
        }
    }

    /**
     * Downloads and installs the current OCI-managed identity and CA certificate.
     *
     * @param initialLoad whether this is the initial TLS installation
     * @return whether TLS material was installed
     */
    boolean loadContext(boolean initialLoad) {
        reloadLock.lock();
        try {
            OciCertificatesDownloader downloader = certDownloader.get();
            OciCertificatesDownloader.CertificatesWithPrivateKey identity =
                    downloader.loadCertificatesWithPrivateKey(cfg.certOcid());
            X509Certificate ca = downloader.loadCACertificate(cfg.caOcid());
            ReloadToken candidate = new ReloadToken(identity.version(), ca);
            if (!cfg.alwaysReload() && candidate.equals(installedMaterial.get())) {
                return false;
            }

            Certificate[] certificates = identity.certificates();
            PrivateKey privateKey = identity.privateKey();
            SecureRandom secureRandom = secureRandom(tlsConfig);
            KeyManagerFactory kmf = buildKmf(tlsConfig, secureRandom, privateKey, certificates);

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
                    .filter(X509KeyManager.class::isInstance)
                    .map(X509KeyManager.class::cast)
                    .findFirst();
            if (keyManager.isEmpty()) {
                throw new IllegalStateException("Unable to find X.509 key manager in download: " + cfg.certOcid());
            }

            Optional<X509TrustManager> trustManager = Arrays.stream(tmf.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst();
            if (trustManager.isEmpty()) {
                throw new IllegalStateException("Unable to find X.509 trust manager in download: " + cfg.certOcid());
            }

            if (initialLoad) {
                initSslContext(tlsConfig, secureRandom, kmf.getKeyManagers(), tmf.getTrustManagers());
            } else {
                reload(keyManager, trustManager);
            }

            installedMaterial.set(candidate);
            return true;
        } catch (KeyStoreException e) {
            throw new IllegalStateException("Error while loading context from OCI", e);
        } finally {
            reloadLock.unlock();
        }
    }

    private record ReloadToken(String identityVersion, X509Certificate caCertificate) {
    }
}
