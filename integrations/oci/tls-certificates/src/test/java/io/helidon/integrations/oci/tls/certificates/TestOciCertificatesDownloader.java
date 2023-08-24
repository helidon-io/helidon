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

import java.io.InputStream;
import java.security.cert.Certificate;
import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;

import jakarta.inject.Singleton;

@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciCertificatesDownloader extends DefaultOciCertificatesDownloader {
    static String version = "1";

    static int callCount;

    void version(String version) {
        TestOciCertificatesDownloader.version = version;
    }

    @Override
    public Certificates loadCertificates(String certOcid) {
        callCount++;

        if (OciTestUtils.ociRealUsage()) {
            return super.loadCertificates(certOcid);
        } else {
            Objects.requireNonNull(certOcid);
            try (InputStream certIs =
                    TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/serverCert.pem")) {
                return OciCertificatesDownloader.create(version, new Certificate[] {toCertificate(certIs)});
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public Certificate loadCACertificate(String caCertOcid) {
        callCount++;

        if (OciTestUtils.ociRealUsage()) {
            return super.loadCACertificate(caCertOcid);
        } else {
            Objects.requireNonNull(caCertOcid);
            try (InputStream caCertIs =
                    TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/ca.pem")) {
                return toCertificate(caCertIs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

}
