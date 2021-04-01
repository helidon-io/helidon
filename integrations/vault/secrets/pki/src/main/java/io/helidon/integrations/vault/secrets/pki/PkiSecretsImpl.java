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
