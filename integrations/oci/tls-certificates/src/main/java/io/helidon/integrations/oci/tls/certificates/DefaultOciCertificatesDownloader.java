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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

import io.helidon.integrations.oci.sdk.runtime.OciExtension;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;

import com.oracle.bmc.certificates.CertificatesClient;
import com.oracle.bmc.certificates.requests.GetCertificateAuthorityBundleRequest;
import com.oracle.bmc.certificates.requests.GetCertificateBundleRequest;
import com.oracle.bmc.certificates.responses.GetCertificateAuthorityBundleResponse;
import com.oracle.bmc.certificates.responses.GetCertificateBundleResponse;
import jakarta.inject.Singleton;

import static io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader.create;

/**
 * Implementation of the {@link OciCertificatesDownloader} that will use OCI's Certificates Service to download certs.
 */
@Singleton
class DefaultOciCertificatesDownloader implements OciCertificatesDownloader {

    @Override
    public Certificates loadCertificates(String certOcid) {
        Objects.requireNonNull(certOcid);
        try {
            return loadCerts(certOcid);
        } catch (CertificateException e) {
            throw new IllegalStateException("Failed to load certificate ocid: " + certOcid, e);
        }
    }

    @Override
    public Certificate loadCACertificate(String caCertOcid) {
        Objects.requireNonNull(caCertOcid);
        try {
            return loadCACert(caCertOcid);
        } catch (CertificateException e) {
            throw new IllegalStateException("Failed to load ca certificate ocid: " + caCertOcid, e);
        }
    }

    static Certificates loadCerts(String certOcid) throws CertificateException {
        try (CertificatesClient client = CertificatesClient.builder()
                .build(OciExtension.ociAuthenticationProvider().get())) {
            GetCertificateBundleResponse res =
                    client.getCertificateBundle(GetCertificateBundleRequest.builder()
                                                        .certificateId(certOcid)
                                                        .build());
            ByteArrayInputStream chainIs = new ByteArrayInputStream(res.getCertificateBundle().getCertChainPem()
                                                                            .getBytes(StandardCharsets.US_ASCII));
            ByteArrayInputStream certIs = new ByteArrayInputStream(res.getCertificateBundle().getCertificatePem()
                                                                           .getBytes(StandardCharsets.US_ASCII));
            Certificate[] certs = toCertificates(chainIs, certIs);
            String version = toVersion(res.getEtag(), certs);
            return create(version, certs);
        }
    }

    static Certificate loadCACert(String caCertOcid) throws CertificateException {
        GetCertificateAuthorityBundleResponse res;
        try (CertificatesClient client = CertificatesClient.builder()
                .build(OciExtension.ociAuthenticationProvider().get())) {
            res = client.getCertificateAuthorityBundle(GetCertificateAuthorityBundleRequest.builder()
                                                               .certificateAuthorityId(caCertOcid)
                                                               .build());

            byte[] pemBytes = res.getCertificateAuthorityBundle().getCertificatePem()
                    .getBytes(StandardCharsets.US_ASCII);
            ByteArrayInputStream certIs = new ByteArrayInputStream(pemBytes);
            return toCertificate(certIs);
        }
    }

    static Certificate[] toCertificates(InputStream chainIs,
                                        InputStream certIs) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert = cf.generateCertificate(certIs);
        ArrayList<Certificate> chain = new ArrayList<>();
        chain.add(cert);
        chain.addAll(cf.generateCertificates(chainIs));
        return chain.toArray(new Certificate[0]);
    }

    static Certificate toCertificate(InputStream certIs) throws CertificateException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return cf.generateCertificate(certIs);
    }

    // use the eTag, defaulting to the hash of the certs if not present
    static String toVersion(String eTag,
                            Certificate[] certs) {
        if (eTag != null && !eTag.isBlank()) {
            return eTag;
        }

        return String.valueOf(Arrays.hashCode(certs));
    }
}
