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

package io.helidon.integrations.oci.tls.certificates.spi;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
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
     * Downloads the certificate chain and its matching private key from the OCI Certificates Service.
     * Implementations predating support for OCI-managed private-key bundles do not need to implement this method.
     *
     * @param certOcid the certificate OCID
     * @return the downloaded certificate chain and private key
     * @throws UnsupportedOperationException if this implementation cannot download a private-key bundle
     * @throws IllegalStateException if there are any errors loading the certificate or key
     * @see #create(String, X509Certificate[], PrivateKey)
     */
    default CertificatesWithPrivateKey loadCertificatesWithPrivateKey(String certOcid) {
        throw new UnsupportedOperationException("Downloading a certificate bundle with its private key is not supported by "
                                                        + getClass().getName());
    }

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
     * Creates a certificate bundle with its matching private key.
     * The first certificate in the array must be the leaf certificate corresponding to the private key.
     *
     * @param version      version identifying the downloaded certificate bundle
     * @param certificates leaf-first certificate chain
     * @param privateKey   private key matching the leaf certificate
     * @return certificate and private-key bundle
     */
    static CertificatesWithPrivateKey create(String version,
                                             X509Certificate[] certificates,
                                             PrivateKey privateKey) {
        Objects.requireNonNull(version, "Version is required");
        if (version.isBlank()) {
            throw new IllegalArgumentException("Version must not be blank");
        }

        X509Certificate[] certificateCopy = Objects.requireNonNull(certificates, "Certificates are required").clone();
        if (certificateCopy.length == 0) {
            throw new IllegalArgumentException("At least one certificate is required");
        }
        if (Arrays.stream(certificateCopy).anyMatch(Objects::isNull)) {
            throw new NullPointerException("Certificates must not contain null elements");
        }

        return new CertificatesWithPrivateKey(version,
                                              certificateCopy,
                                              Objects.requireNonNull(privateKey, "Private key is required"));
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

    /**
     * Represents a leaf-first certificate chain, its matching private key, and its version identifier.
     */
    final class CertificatesWithPrivateKey {
        private final String version;
        private final X509Certificate[] certificates;
        private final PrivateKey privateKey;

        private CertificatesWithPrivateKey(String version,
                                           X509Certificate[] certificates,
                                           PrivateKey privateKey) {
            this.version = version;
            this.certificates = certificates;
            this.privateKey = privateKey;
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
         * The leaf-first certificate chain.
         *
         * @return a copy of the certificate chain
         */
        public X509Certificate[] certificates() {
            return certificates.clone();
        }

        /**
         * The private key matching the leaf certificate.
         *
         * @return private key
         */
        public PrivateKey privateKey() {
            return privateKey;
        }
    }

}
