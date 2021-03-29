/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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

package io.helidon.integrations.vault.secrets.pki;

import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Optional;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.vault.Engine;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Secrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

/**
 * API operation for Vault PKI Secrets Engine.
 */
public interface PkiSecrets extends Secrets {
    /**
     * PKI secrets engine.
     * <p>
     * Documentation:
     * <a href="https://www.vaultproject.io/api-docs/secret/pki">https://www.vaultproject.io/api-docs/secret/pki</a>
     */
    Engine<PkiSecrets> ENGINE = Engine.create(PkiSecrets.class, "pki", "pki");
    /**
     * RSA algorithm for keys.
     */
    String KEY_TYPE_RSA = "rsa";
    /**
     * EC (Elliptic curve) algorithm for keys.
     */
    String KEY_TYPE_EC = "ec";

    /**
     * List certificate serial numbers.
     * @param request request, path is ignored
     * @return serial numbers of certificates
     */
    @Override
    Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request);

    /**
     * Certification authority certificate.
     *
     * @return certificate of the CA
     */
    default Single<X509Certificate> caCertificate() {
        return caCertificate(CaCertificateGet.Request.builder())
                .map(CaCertificateGet.Response::toCertificate);
    }

    /**
     * Certification authority certificate in raw bytes.
     *
     * @param format format to use, either {@code DER} or {@code PEM} format are supported
     * @return CA certificate bytes
     */
    default Single<byte[]> caCertificate(PkiFormat format) {
        return caCertificate(CaCertificateGet.Request.builder()
                                     .format(format))
                .map(CaCertificateGet.Response::toBytes);
    }

    /**
     * Certification authority certificate.
     *
     * @param request request with optional {@link io.helidon.integrations.vault.secrets.pki.PkiFormat}
     *                configured
     * @return CA certificate bytes
     */
    Single<CaCertificateGet.Response> caCertificate(CaCertificateGet.Request request);

    /**
     * Certificate with the defined serial id.
     *
     * @param serialNumber serial number of the certificate
     * @return certificate, if not found, an exception is returned
     */
    default Single<Optional<X509Certificate>> certificate(String serialNumber) {
        return certificate(CertificateGet.Request.builder()
                                   .serialNumber(serialNumber))
                .map(VaultOptionalResponse::entity)
                .map(it -> it.map(CertificateGet.Response::toCertificate));
    }

    /**
     * Certificate in raw bytes, currently only {@link io.helidon.integrations.vault.secrets.pki.PkiFormat#PEM} is supported.
     *
     * @param serialNumber serial number of the certificate
     * @param format format - must be {@link io.helidon.integrations.vault.secrets.pki.PkiFormat#PEM}
     * @return certificate bytes in {@code PEM} format
     */
    default Single<Optional<byte[]>> certificate(String serialNumber, PkiFormat format) {
        return certificate(CertificateGet.Request.builder()
                                   .serialNumber(serialNumber)
                                   .format(format))
                .map(VaultOptionalResponse::entity)
                .map(it -> it.map(CertificateGet.Response::toBytes));
    }

    Single<VaultOptionalResponse<CertificateGet.Response>> certificate(CertificateGet.Request request);

    /**
     * Certificate revocation list.
     *
     * @return revoke list
     */
    default Single<X509CRL> crl() {
        return crl(CrlGet.Request.builder())
                .map(CrlGet.Response::toCrl);
    }

    /**
     * Certificate revocation list in raw bytes.
     *
     * @param format to choose between {@code PEM} and {@code DER} encoding of the list
     * @return CRL bytes
     */
    default Single<byte[]> crl(PkiFormat format) {
        return crl(CrlGet.Request.builder()
                           .format(format))
                .map(CrlGet.Response::toBytes);
    }

    Single<CrlGet.Response> crl(CrlGet.Request request);

    /**
     * Issue a new certificate returning raw data.
     * <p>
     * The format of data returned depends on the {@link io.helidon.integrations.vault.secrets.pki.PkiFormat} chosen:
     * <ul>
     *     <li>{@link PkiFormat#PEM} - pem bytes (e.g. {@code -----BEGIN CERTIFICATE-----...})</li>
     *     <li>{@link PkiFormat#PEM_BUNDLE} - same as above, with certificate bundling the private key</li>
     *     <li>{@link PkiFormat#DER} - binary encoding</li>
     * </ul>
     * @param request configuration of the new certificate
     * @return certificate response with bytes of returned certificates
     */
    Single<IssueCertificate.Response> issueCertificate(IssueCertificate.Request request);

    /**
     * This endpoint signs a new certificate based upon the provided CSR and the supplied parameters, subject to the
     * restrictions contained in the role named in the endpoint.
     *
     * @param request sign CSR request
     * @return a new certificate
     */
    Single<SignCsr.Response> signCertificateRequest(SignCsr.Request request);

    /**
     * Revoke a certificate by its serial number.
     * @param serialNumber serial number of the certificate to revoke
     * @return revocation instant
     */
    default Single<Instant> revokeCertificate(String serialNumber) {
        return revokeCertificate(RevokeCertificate.Request.builder()
                                         .serialNumber(serialNumber))
                .map(RevokeCertificate.Response::revocationTime);
    }

    Single<RevokeCertificate.Response> revokeCertificate(RevokeCertificate.Request request);

    /**
     * Generate a self signed root certificate.
     * This operations makes sense for testing.
     * For production environments, this would most likely be initialized with an explicit
     * key and certificate.
     *
     * @param commonName the common name (cn) of the certificate
     * @return when request finishes
     */
    default Single<GenerateSelfSignedRoot.Response> generateSelfSignedRoot(String commonName) {
        return generateSelfSignedRoot(GenerateSelfSignedRoot.Request.builder()
                                              .commonName(commonName));
    }

    Single<GenerateSelfSignedRoot.Response> generateSelfSignedRoot(GenerateSelfSignedRoot.Request request);

    /**
     * This endpoint creates or updates the role definition.
     * Note that the {@link PkiRole.Request#addAllowedDomain(String)},
     * {@link PkiRole.Request#allowSubDomains(boolean)}, {@link PkiRole.Request#allowGlobDomains(boolean)}, and
     * {@link PkiRole.Request#allowAnyName(boolean)} are additive; between these options, and across multiple roles,  nearly any
     * issuing policy can be accommodated.
     * {@link PkiRole.Request#serverFlag(boolean)}, {@link PkiRole.Request#clientFlag(boolean)},
     * and {@link PkiRole.Request#codeSigningFlag(boolean)} are additive as well. If a client
     * requests a certificate that is not allowed by the CN policy in the role, the request is denied.
     *
     * @param request request modifying the role
     * @return when request finishes
     */
    Single<PkiRole.Response> createOrUpdateRole(PkiRole.Request request);
}
