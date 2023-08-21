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

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Objects;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import io.helidon.common.tls.ConfiguredTlsManager;
import io.helidon.common.tls.Tls;
import io.helidon.common.tls.TlsConfig;
import io.helidon.config.Config;
import io.helidon.inject.api.InjectionServices;
import io.helidon.integrations.oci.sdk.runtime.OciExtension;

import com.oracle.bmc.certificates.CertificatesClient;
import com.oracle.bmc.certificates.requests.GetCertificateAuthorityBundleRequest;
import com.oracle.bmc.certificates.requests.GetCertificateBundleRequest;
import com.oracle.bmc.certificates.responses.GetCertificateAuthorityBundleResponse;
import com.oracle.bmc.certificates.responses.GetCertificateBundleResponse;
import jakarta.inject.Provider;

/**
 * The default implementation (service loader and provider-driven) implementation of {@link OciCertificatesTlsManager}.
 *
 * @see DefaultOciCertificatesTlsManagerProvider
 */
class DefaultOciCertificatesTlsManager extends ConfiguredTlsManager implements OciCertificatesTlsManager {
    static final String TYPE = "oci-certificates-tls-manager";

    private final OciCertificatesTlsManagerConfig cfg;
    private final Provider<OciPrivateKeyDownloader> pkDownloader;

    DefaultOciCertificatesTlsManager(OciCertificatesTlsManagerConfig cfg,
                                     Config config,
                                     String name) {
        super(config, name, TYPE);
        this.cfg = Objects.requireNonNull(cfg);
        this.pkDownloader = InjectionServices.realizedServices().lookupFirst(OciPrivateKeyDownloader.class);
        tls(load());
    }

    @Override
    public OciCertificatesTlsManagerConfig prototype() {
        return cfg;
    }

    private Tls load() {
        TlsConfig.Builder tlsConfigBuilder = TlsConfig.builder()
                .manager(this);
        loadSSLContext(tlsConfigBuilder);
        return tlsConfigBuilder.build();
    }

    private void loadSSLContext(TlsConfig.Builder builder) {
        try {
            Certificate[] certificates = loadCert(cfg.certOcid());
            Certificate ca = loadCACert();
            PrivateKey key = pkDownloader.get().loadKey(cfg.keyOcid(), cfg.vaultCryptoEndpoint());

            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            keyStore.setKeyEntry("server-cert-chain", key, cfg.keyPassword().get(), certificates);
            keyStore.setCertificateEntry("trust-ca", ca);

            SSLContext context = SSLContext.getInstance("TLS");
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

            kmf.init(keyStore, cfg.keyPassword().get());
            tmf.init(keyStore);

            // Uncomment to debug downloaded context
            //saveToFile(keyStore, type + ".jks");

            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), SecureRandom.getInstance("DEFAULT"));
            builder.sslContext(context);
            // TODO:
//            builder.tlsInfo(new TlsInternalInfo(boolean explicitContext,
    //            List<TlsReloadableComponent> reloadableComponents,
    //            X509TrustManager originalTrustManager,
    //            X509KeyManager originalKeyManager) {
//            builder.sslParameters(???)
        } catch (Exception e) {
            throw new RuntimeException("Error when loading mTls context from OCI", e);
        }
    }

    private Certificate[] loadCert(String certOcid) throws Exception {
        try (CertificatesClient client = CertificatesClient.builder()
                .build(OciExtension.ociAuthenticationProvider().get())) {
            GetCertificateBundleResponse res =
                    client.getCertificateBundle(GetCertificateBundleRequest.builder()
                                                        .certificateId(certOcid)
                                                        .build());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream chainIs = new ByteArrayInputStream(res.getCertificateBundle().getCertChainPem().getBytes());
            ByteArrayInputStream certIs = new ByteArrayInputStream(res.getCertificateBundle().getCertificatePem().getBytes());
            Certificate cert = cf.generateCertificate(certIs);
            ArrayList<Certificate> chain = new ArrayList<>();
            chain.add(cert);
            chain.addAll(cf.generateCertificates(chainIs));
            return chain.toArray(new Certificate[0]);
        }
    }

    private Certificate loadCACert() throws Exception {
        GetCertificateAuthorityBundleResponse res;
        try (CertificatesClient client = CertificatesClient.builder()
                .build(OciExtension.ociAuthenticationProvider().get())) {
            res = client.getCertificateAuthorityBundle(GetCertificateAuthorityBundleRequest.builder()
                                                               .certificateAuthorityId(cfg.caOcid())
                                                               .build());

            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            byte[] pemBytes = res.getCertificateAuthorityBundle().getCertificatePem().getBytes();
            try (ByteArrayInputStream pemStream = new ByteArrayInputStream(pemBytes)) {
                return cf.generateCertificate(pemStream);
            }
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
