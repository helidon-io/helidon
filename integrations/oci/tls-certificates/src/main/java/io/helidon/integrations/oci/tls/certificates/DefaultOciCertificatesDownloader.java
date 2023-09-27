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
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.helidon.common.Prioritized;
import io.helidon.common.pki.PemReader;
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
public class DefaultOciCertificatesDownloader implements OciCertificatesDownloader, Prioritized {

    /**
     * Service loader based constructor.
     *
     * @deprecated this is a Java ServiceLoader implementation and the constructor should not be used directly
     */
    @Deprecated
    public DefaultOciCertificatesDownloader() {
    }

    @Override
    public Certificates loadCertificates(String certOcid) {
        Objects.requireNonNull(certOcid);
        try {
            return loadCerts(certOcid);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to load certificate ocid: " + certOcid, e);
        }
    }

    @Override
    public X509Certificate loadCACertificate(String caCertOcid) {
        Objects.requireNonNull(caCertOcid);
        try {
            return loadCACert(caCertOcid);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to load ca certificate ocid: " + caCertOcid, e);
        }
    }

    static Certificates loadCerts(String certOcid) {
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
            X509Certificate[] certs = toCertificates(chainIs, certIs);
            String version = toVersion(res.getEtag(), certs);
            return create(version, certs);
        }
    }

    static X509Certificate loadCACert(String caCertOcid) {
        GetCertificateAuthorityBundleResponse res;
        try (CertificatesClient client = CertificatesClient.builder()
                .build(OciExtension.ociAuthenticationProvider().get())) {
            res = client.getCertificateAuthorityBundle(GetCertificateAuthorityBundleRequest.builder()
                                                               .certificateAuthorityId(caCertOcid)
                                                               .build());

            ByteArrayInputStream certIs = new ByteArrayInputStream(res.getCertificateAuthorityBundle().getCertificatePem()
                    .getBytes(StandardCharsets.US_ASCII));
            return toCertificate(certIs);
        }
    }

    static X509Certificate[] toCertificates(InputStream chainIs,
                                            InputStream certIs) {
        ArrayList<X509Certificate> chain = new ArrayList<>();
        chain.addAll(PemReader.readCertificates(certIs));
        chain.addAll(PemReader.readCertificates(chainIs));
        return chain.toArray(new X509Certificate[0]);
    }

    static X509Certificate toCertificate(InputStream certIs) {
        List<X509Certificate> certs = PemReader.readCertificates(certIs);
        if (certs.size() != 1) {
            throw new IllegalStateException("Expected a single certificate in stream but found: " + certs.size());
        }
        return certs.get(0);
    }

    // use the eTag, defaulting to the hash of the certs if not present
    static String toVersion(String eTag,
                            Certificate[] certs) {
        if (eTag != null && !eTag.isBlank()) {
            return eTag;
        }

        return String.valueOf(Arrays.hashCode(certs));
    }

    @Override
    public int priority() {
        return DEFAULT_PRIORITY;
    }
}
