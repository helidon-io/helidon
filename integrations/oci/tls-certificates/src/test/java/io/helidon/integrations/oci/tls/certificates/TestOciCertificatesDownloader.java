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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.service.registry.Service;

@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciCertificatesDownloader implements OciCertificatesDownloader {
    static String version = "1";

    static volatile int callCount_loadCertificates;
    static volatile int callCount_loadCACertificate;

    private final Supplier<DefaultOciCertificatesDownloader> realDownloader;

    TestOciCertificatesDownloader(Supplier<DefaultOciCertificatesDownloader> realDownloader) {
        this.realDownloader = realDownloader;
    }

    @Override
    public Certificates loadCertificates(String certOcid) {
        callCount_loadCertificates++;

        try {
            if (OciTestUtils.ociRealUsage()) {
                return realDownloader.get().loadCertificates(certOcid);
            } else {
                TimeUnit.MILLISECONDS.sleep(1); // make sure metrics timestamp changes
                Objects.requireNonNull(certOcid);
                try (InputStream certIs =
                        TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/serverCert.pem")) {
                    return OciCertificatesDownloader.create(version, new X509Certificate[] {DefaultOciCertificatesDownloader.toCertificate(certIs)});
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (InterruptedException e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

    @Override
    public X509Certificate loadCACertificate(String caCertOcid) {
        callCount_loadCACertificate++;

        try {
            if (OciTestUtils.ociRealUsage()) {
                return realDownloader.get().loadCACertificate(caCertOcid);
            } else {
                TimeUnit.MILLISECONDS.sleep(1); // make sure metrics timestamp changes
                Objects.requireNonNull(caCertOcid);
                try (InputStream caCertIs =
                        TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/ca.pem")) {
                    return DefaultOciCertificatesDownloader.toCertificate(caCertIs);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } catch (InterruptedException e) {
            System.getLogger(getClass().getName()).log(System.Logger.Level.ERROR, e.getMessage(), e);
            throw new IllegalStateException(e);
        }
    }

}
