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

package io.helidon.integrations.oci.tls.certificates.spi;

import java.security.cert.X509Certificate;
import java.util.Objects;

import io.helidon.service.registry.Service;

/**
 * The contract used for downloading certificates from OCI.
 */
@Service.Contract
public interface OciCertificatesDownloader {

    /**
     * The implementation will download the certificate chain identified by the given ocid from the OCI Certificates Service.
     *
     * @param certOcid the cert ocid
     * @return the downloaded certificate chain
     * @throws IllegalStateException if there is any errors loading the key
     * @see #create(String, X509Certificate[])
     */
    Certificates loadCertificates(String certOcid);

    /**
     * The implementation will download the CA certificate identified by the given ocid from the OCI Certificates Services.
     *
     * @param caCertOcid the ca cert ocid
     * @return the downloaded CA certificate
     * @throws IllegalStateException if there is any errors loading the key
     */
    X509Certificate loadCACertificate(String caCertOcid);

    /**
     * Creates a Certificates instance given its version and array of certificates. The version is used to identify change - the
     * format of the string is immaterial. Only when it changes it will signify the need for reloading.
     *
     * @param version       the version
     * @param certificates  the certificates
     * @return a certificates wrapper
     */
    static Certificates create(String version,
                               X509Certificate[] certificates) {
        if (Objects.requireNonNull(version, "Version is required").isBlank()) {
            throw new IllegalArgumentException();
        }

        return new Certificates(version, Objects.requireNonNull(certificates));
    }

    /**
     * Represents the certificate chain as well as the version identifier of the downloaded certificates.
     */
    class Certificates {
        private final String version;
        private final X509Certificate[] certificates;

        private Certificates(String version,
                             X509Certificate[] certificates) {
            this.version = version;
            this.certificates = certificates;
        }

        /**
         * The version identifier.
         *
         * @return version
         */
        public String version() {
            return version;
        }

        /**
         * The certificates.
         *
         * @return certificates
         */
        public X509Certificate[] certificates() {
            return certificates;
        }
    }

}
