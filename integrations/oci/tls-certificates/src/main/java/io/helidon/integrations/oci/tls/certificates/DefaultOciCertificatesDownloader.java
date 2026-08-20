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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.common.configurable.Resource;
import io.helidon.common.pki.Keys;
import io.helidon.common.pki.PemReader;
import io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader;
import io.helidon.service.registry.Service;

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.certificates.CertificatesClient;
import com.oracle.bmc.certificates.model.CertificateBundleWithPrivateKey;
import com.oracle.bmc.certificates.requests.GetCertificateAuthorityBundleRequest;
import com.oracle.bmc.certificates.requests.GetCertificateBundleRequest;
import com.oracle.bmc.certificates.responses.GetCertificateAuthorityBundleResponse;
import com.oracle.bmc.certificates.responses.GetCertificateBundleResponse;

import static io.helidon.integrations.oci.tls.certificates.spi.OciCertificatesDownloader.create;

/**
 * Implementation of the {@link OciCertificatesDownloader} that will use OCI's Certificates Service to download certs.
 */
@Service.Provider
@Weight(Weighted.DEFAULT_WEIGHT - 10)
class DefaultOciCertificatesDownloader implements OciCertificatesDownloader {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AbstractAuthenticationDetailsProvider authProvider;

    DefaultOciCertificatesDownloader(AbstractAuthenticationDetailsProvider authProvider) {
        this.authProvider = authProvider;
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
    public CertificatesWithPrivateKey loadCertificatesWithPrivateKey(String certOcid) {
        Objects.requireNonNull(certOcid);
        try {
            return loadCertsWithPrivateKey(certOcid);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to load certificate bundle with private key for ocid: " + certOcid,
                                            e);
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

    Certificates loadCerts(String certOcid) {
        try (CertificatesClient client = CertificatesClient.builder()
                .build(authProvider)) {
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

    CertificatesWithPrivateKey loadCertsWithPrivateKey(String certOcid) {
        try (CertificatesClient client = CertificatesClient.builder()
                .build(authProvider)) {
            GetCertificateBundleResponse response =
                    client.getCertificateBundle(GetCertificateBundleRequest.builder()
                                                        .certificateId(Objects.requireNonNull(certOcid))
                                                        .stage(GetCertificateBundleRequest.Stage.Current)
                                                        .certificateBundleType(
                                                                GetCertificateBundleRequest.CertificateBundleType
                                                                        .CertificateContentWithPrivateKey)
                                                        .build());
            return toCertificatesWithPrivateKey(response);
        }
    }

    X509Certificate loadCACert(String caCertOcid) {
        GetCertificateAuthorityBundleResponse res;
        try (CertificatesClient client = CertificatesClient.builder()
                .build(authProvider)) {
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
        return certs.getFirst();
    }

    static CertificatesWithPrivateKey toCertificatesWithPrivateKey(GetCertificateBundleResponse response) {
        Objects.requireNonNull(response);
        if (!(response.getCertificateBundle() instanceof CertificateBundleWithPrivateKey bundle)) {
            throw invalidBundle("the response does not contain a private key");
        }

        String certificatePem = requireBundleValue(bundle.getCertificatePem(), "the leaf certificate is missing");
        String privateKeyPem = requireBundleValue(bundle.getPrivateKeyPem(), "the private key is missing");
        String certChainPem = bundle.getCertChainPem();
        X509Certificate[] certificates = parseCertificates(certificatePem,
                                                           certChainPem == null ? "" : certChainPem);

        String privateKeyPemPassphrase = bundle.getPrivateKeyPemPassphrase();
        char[] passphrase = privateKeyPemPassphrase == null || privateKeyPemPassphrase.isEmpty()
                ? null
                : privateKeyPemPassphrase.toCharArray();
        PrivateKey privateKey;
        try {
            privateKey = parsePrivateKey(privateKeyPem, passphrase);
        } finally {
            if (passphrase != null) {
                Arrays.fill(passphrase, '\0');
            }
        }

        validatePrivateKey(certificates[0], privateKey);
        String version = toVersion(bundle.getVersionNumber(), response.getEtag(), certificates);
        return create(version, certificates, privateKey);
    }

    static PrivateKey parsePrivateKey(String privateKeyPem, char[] passphrase) {
        try {
            Keys.Builder builder = Keys.builder()
                    .pem(pem -> {
                        pem.key(Resource.create("OCI managed certificate private key", privateKeyPem));
                        if (passphrase != null) {
                            pem.keyPassphrase(passphrase);
                        }
                    });
            return builder.build()
                    .privateKey()
                    .orElseThrow(() -> invalidBundle("the private key cannot be decoded"));
        } catch (RuntimeException e) {
            throw invalidBundle("the private key cannot be decoded");
        }
    }

    static void validatePrivateKey(X509Certificate certificate, PrivateKey privateKey) {
        String signatureAlgorithm = switch (privateKey.getAlgorithm()) {
            case "RSA" -> "SHA256withRSA";
            case "EC" -> "SHA256withECDSA";
            default -> throw invalidBundle("the private key algorithm is not supported");
        };

        try {
            byte[] challenge = new byte[32];
            RANDOM.nextBytes(challenge);

            Signature signer = Signature.getInstance(signatureAlgorithm);
            signer.initSign(privateKey);
            signer.update(challenge);

            Signature verifier = Signature.getInstance(signatureAlgorithm);
            verifier.initVerify(certificate.getPublicKey());
            verifier.update(challenge);
            if (!verifier.verify(signer.sign())) {
                throw invalidBundle("the private key does not match the leaf certificate");
            }
        } catch (GeneralSecurityException e) {
            throw invalidBundle("the private key does not match the leaf certificate");
        }
    }

    static String toVersion(Long versionNumber,
                            String eTag,
                            Certificate[] certs) {
        if (versionNumber != null) {
            return versionNumber.toString();
        }
        return toVersion(eTag, certs);
    }

    // use the eTag, defaulting to the hash of the certs if not present
    static String toVersion(String eTag,
                            Certificate[] certs) {
        if (eTag != null && !eTag.isBlank()) {
            return eTag;
        }

        return String.valueOf(Arrays.hashCode(certs));
    }

    private static X509Certificate[] parseCertificates(String certificatePem, String certChainPem) {
        List<X509Certificate> leafCertificates;
        try {
            leafCertificates = PemReader.readCertificates(
                    new ByteArrayInputStream(certificatePem.getBytes(StandardCharsets.US_ASCII)));
        } catch (RuntimeException e) {
            throw invalidBundle("the leaf certificate cannot be decoded");
        }
        if (leafCertificates.size() != 1) {
            throw invalidBundle("the bundle must contain exactly one leaf certificate");
        }

        ArrayList<X509Certificate> certificates = new ArrayList<>(leafCertificates);
        if (!certChainPem.isBlank()) {
            try {
                certificates.addAll(PemReader.readCertificates(
                        new ByteArrayInputStream(certChainPem.getBytes(StandardCharsets.US_ASCII))));
            } catch (RuntimeException e) {
                throw invalidBundle("the certificate chain cannot be decoded");
            }
        }
        return certificates.toArray(X509Certificate[]::new);
    }

    private static String requireBundleValue(String value, String error) {
        if (value == null || value.isBlank()) {
            throw invalidBundle(error);
        }
        return value;
    }

    private static IllegalStateException invalidBundle(String reason) {
        return new IllegalStateException("Invalid OCI managed certificate bundle: " + reason);
    }
}
