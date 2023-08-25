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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509KeyManager;
import javax.net.ssl.X509TrustManager;

import io.helidon.common.tls.ConfiguredTlsManager;
import io.helidon.common.tls.TlsConfig;
import io.helidon.config.Config;
import io.helidon.faulttolerance.Async;
import io.helidon.inject.api.InjectionServices;
import io.helidon.inject.api.Services;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.integrations.oci.tls.certificates.spi.OciPrivateKeyDownloader;

import jakarta.inject.Provider;

/**
 * The default implementation (service loader and provider-driven) implementation of {@link OciCertificatesTlsManager}.
 *
 * @see DefaultOciCertificatesTlsManagerProvider
 */
// consider adding metrics around this in the future (number of calls to check for download, number of new downloads, etc.)
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
        try {
            Services services = InjectionServices.realizedServices();
            this.pkDownloader = services.lookupFirst(OciPrivateKeyDownloader.class);
            this.certDownloader = services.lookupFirst(OciCertificatesDownloader.class);
            this.asyncExecutor = Executors.newSingleThreadScheduledExecutor();
            this.async = Async.builder().executor(asyncExecutor).build();

            // the initial loading of the tls
            loadContext(true);

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
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize", e);
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
                return false;
            }
            Certificate ca = cd.loadCACertificate(cfg.caOcid());

            OciPrivateKeyDownloader pd = pkDownloader.get();
            PrivateKey key = pd.loadKey(cfg.keyOcid(), cfg.vaultCryptoEndpoint());


            SecureRandom secureRandom = secureRandom(tlsConfig);

            KeyManagerFactory kmf = buildKmf(tlsConfig, secureRandom, key, certificates.certificates());

            TrustManagerFactory tmf = tmf(tlsConfig);
            KeyStore keyStore = internalKeystore(tlsConfig);
            keyStore.setCertificateEntry("trust-ca", ca);
            tmf.init(keyStore);

            // Uncomment to debug downloaded context
            // saveToFile(keyStore, type + ".jks");

            Optional<X509KeyManager> keyManager = Arrays.stream(kmf.getKeyManagers())
                    .filter(m -> m instanceof X509KeyManager)
                    .map(X509KeyManager.class::cast)
                    .findFirst();
            if (keyManager.isEmpty()) {
                throw new RuntimeException("Unable to find X.509 key manager in download");
            }

            Optional<X509TrustManager> trustManager = Arrays.stream(tmf.getTrustManagers())
                    .filter(m -> m instanceof X509TrustManager)
                    .map(X509TrustManager.class::cast)
                    .findFirst();
            if (trustManager.isEmpty()) {
                throw new RuntimeException("Unable to find X.509 trust manager in download");
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

    //    private void saveToFile(KeyStore ks, String fileName) {
    //        try {
    //            FileOutputStream fos = new FileOutputStream(fileName);
    //            ks.store(fos, new char[0]);
    //        } catch (Exception e) {
    //            throw new RuntimeException(e);
    //        }
    //    }

}
