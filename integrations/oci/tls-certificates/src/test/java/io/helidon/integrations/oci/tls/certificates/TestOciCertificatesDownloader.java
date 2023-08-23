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

import jakarta.inject.Singleton;

@Singleton
@Weight(Weighted.DEFAULT_WEIGHT + 1)
class TestOciCertificatesDownloader extends DefaultOciCertificatesDownloader {

    static int callCount;

    @Override
    public Certificate[] loadCertificates(String certOcid) {
        callCount++;

//        return super.loadCertificates(certOcid);
        Objects.requireNonNull(certOcid);
        try (InputStream certIs =
                TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/serverCert.pem")) {
            return new Certificate[] {toCertificate(certIs)};
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Certificate loadCACertificate(String caCertOcid) {
        callCount++;

//        return super.loadCACertificate(caCertOcid);
        Objects.requireNonNull(caCertOcid);
        try (InputStream caCertIs =
                TestOciCertificatesDownloader.class.getClassLoader().getResourceAsStream("test-keys/ca.pem")) {
            return toCertificate(caCertIs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
