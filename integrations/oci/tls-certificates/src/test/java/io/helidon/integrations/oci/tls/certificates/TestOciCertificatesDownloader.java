/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemKeys;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.service.registry.Service;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciCertificatesDownloader implements OciCertificatesDownloader {
    private static final Queue<Supplier<String>> SCRIPTED_CA_OUTCOMES = new ConcurrentLinkedQueue<>();

    static String version = "1";
    static volatile String caCertificateResource = "test-keys/ca.pem";

    static volatile int callCount_loadCertificates;
    static volatile int callCount_loadCertificatesWithPrivateKey;
    static volatile int callCount_loadCACertificate;
    static volatile long managedDelayMillis;
    static volatile RuntimeException managedFailure;
    static volatile RuntimeException caFailure;

    static void scriptCaCertificate(String resource) {
        SCRIPTED_CA_OUTCOMES.add(() -> Objects.requireNonNull(resource));
    }

    static void scriptCaFailure(RuntimeException failure) {
        RuntimeException scriptedFailure = Objects.requireNonNull(failure);
        SCRIPTED_CA_OUTCOMES.add(() -> {
            throw scriptedFailure;
        });
    }

    static void clearCaScript() {
        SCRIPTED_CA_OUTCOMES.clear();
    }

    @Override
    public Certificates loadCertificates(String certOcid) {
        callCount_loadCertificates++;

        try {
            TimeUnit.MILLISECONDS.sleep(1); // make sure metrics timestamp changes
            Objects.requireNonNull(certOcid);
            try (InputStream certIs =
                    TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/serverCert.pem")) {
                X509Certificate certificate = DefaultOciCertificatesDownloader.toCertificate(certIs);
                return OciCertificatesDownloader.create(version, new X509Certificate[] {certificate});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (InterruptedException e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public CertificatesWithPrivateKey loadCertificatesWithPrivateKey(String certOcid) {
        callCount_loadCertificatesWithPrivateKey++;

        try {
            TimeUnit.MILLISECONDS.sleep(managedDelayMillis);
            Objects.requireNonNull(certOcid);
            if (managedFailure != null) {
                throw managedFailure;
            }

            ClassLoader classLoader = TestOciCertificatesDownloader.class.getClassLoader();
            String certificateResource = "2".equals(version) ? "test-keys/ecCert.pem" : "test-keys/serverCert.pem";
            String keyResource = "2".equals(version) ? "test-keys/ecKey.pem" : "test-keys/serverKey.pem";
            try (InputStream certIs = classLoader.getResourceAsStream(certificateResource);
                    InputStream keyIs = classLoader.getResourceAsStream(keyResource)) {
                X509Certificate certificate = DefaultOciCertificatesDownloader.toCertificate(certIs);
                String keyPem = new String(Objects.requireNonNull(keyIs).readAllBytes(), StandardCharsets.US_ASCII);
                PemKeys pemKeys = PemKeys.builder()
                        .key(Resource.create("test private key", keyPem))
                        .build();
                PrivateKey privateKey = Keys.builder()
                        .pem(pemKeys)
                        .build()
                        .privateKey()
                        .orElseThrow();
                return OciCertificatesDownloader.create(version,
                                                        new X509Certificate[] {certificate},
                                                        privateKey);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public X509Certificate loadCACertificate(String caCertOcid) {
        callCount_loadCACertificate++;
        Supplier<String> scriptedOutcome = SCRIPTED_CA_OUTCOMES.poll();
        if (scriptedOutcome == null && caFailure != null) {
            throw caFailure;
        }
        String certificateResource = scriptedOutcome == null ? caCertificateResource : scriptedOutcome.get();

        try {
            TimeUnit.MILLISECONDS.sleep(1); // make sure metrics timestamp changes
            Objects.requireNonNull(caCertOcid);
            try (InputStream caCertIs =
                    TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream(certificateResource)) {
                return DefaultOciCertificatesDownloader.toCertificate(caCertIs);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } catch (InterruptedException e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

}
