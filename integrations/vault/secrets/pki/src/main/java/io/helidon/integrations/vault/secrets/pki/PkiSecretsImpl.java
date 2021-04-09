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

import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.VaultOptionalResponse;

class PkiSecretsImpl implements PkiSecrets {
    private final PkiSecretsRx delegate;

    PkiSecretsImpl(PkiSecretsRx delegate) {
        this.delegate = delegate;
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        return delegate.list(request).await();
    }

    @Override
    public CaCertificateGet.Response caCertificate(CaCertificateGet.Request request) {
        return delegate.caCertificate(request).await();
    }

    @Override
    public VaultOptionalResponse<CertificateGet.Response> certificate(CertificateGet.Request request) {
        return delegate.certificate(request).await();
    }

    @Override
    public CrlGet.Response crl(CrlGet.Request request) {
        return delegate.crl(request).await();
    }

    @Override
    public IssueCertificate.Response issueCertificate(IssueCertificate.Request request) {
        return delegate.issueCertificate(request).await();
    }

    @Override
    public SignCsr.Response signCertificateRequest(SignCsr.Request request) {
        return delegate.signCertificateRequest(request).await();
    }

    @Override
    public RevokeCertificate.Response revokeCertificate(RevokeCertificate.Request request) {
        return delegate.revokeCertificate(request).await();
    }

    @Override
    public GenerateSelfSignedRoot.Response generateSelfSignedRoot(GenerateSelfSignedRoot.Request request) {
        return delegate.generateSelfSignedRoot(request).await();
    }

    @Override
    public PkiRole.Response createOrUpdateRole(PkiRole.Request request) {
        return delegate.createOrUpdateRole(request).await();
    }
}
