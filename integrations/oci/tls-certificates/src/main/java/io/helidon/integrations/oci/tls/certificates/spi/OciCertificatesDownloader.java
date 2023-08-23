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

package io.helidon.integrations.oci.tls.certificates.spi;

import java.security.cert.Certificate;

import io.helidon.inject.api.Contract;

/**
 * The contract used for downloading certificates from OCI.
 */
@Contract
public interface OciCertificatesDownloader {

    /**
     * The implementation will download the certificate chain identified by the given ocid from the OCI Certificates Service.
     *
     * @param certOcid the cert ocid
     * @return the downloaded certificate chain
     * @throws RuntimeException if there is any errors loading the key
     */
    Certificate[] loadCertificates(String certOcid);

    /**
     * The implementation will download the CA certificate identified by the given ocid from the OCI Certificates Services.
     *
     * @param caCertOcid the ca cert ocid
     * @return the downloaded CA certificate
     * @throws RuntimeException if there is any errors loading the key
     */
    Certificate loadCACertificate(String caCertOcid);

}
