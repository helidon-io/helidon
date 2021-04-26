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

import java.util.function.Function;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.integrations.vault.VaultRestException;

class PkiSecretsRxImpl implements PkiSecretsRx {
    private final RestApi restApi;
    private final String mount;

    PkiSecretsRxImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request) {
        String apiPath = mount + "/certs";

        return restApi.invokeOptional(Vault.LIST,
                                      apiPath,
                                      request,
                                      VaultOptionalResponse.<ListSecrets.Response, JsonObject>vaultResponseBuilder()
                                              .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public Single<CaCertificateGet.Response> caCertificate(CaCertificateGet.Request request) {
        String apiPath;
        switch (request.format()) {
        case DER:
            apiPath = mount + "/ca";
            break;
        case PEM:
        case PEM_BUNDLE:
            apiPath = mount + "/ca_chain";
            break;
        default:
            return Single.error(new VaultApiException("Unsupported PKI Format: " + request.format()));
        }

        return restApi.getBytes(apiPath,
                                request,
                                VaultOptionalResponse.<byte[], byte[]>vaultResponseBuilder()
                                        .entityProcessor(Function.identity()))
                .map(optionalResponse -> CaCertificateGet.Response.builder()
                        .entity(optionalResponse.entity().orElseThrow(() -> VaultRestException.builder()
                                .headers(optionalResponse.headers())
                                .requestId(optionalResponse.requestId())
                                .status(optionalResponse.status())
                                .vaultErrors(optionalResponse.errors())
                                .message("CA Certificate is expected to be present, but is not available")
                                .build()))
                        .headers(optionalResponse.headers())
                        .requestId(optionalResponse.requestId())
                        .status(optionalResponse.status())
                        .build());
    }

    @Override
    public Single<VaultOptionalResponse<CertificateGet.Response>> certificate(CertificateGet.Request request) {
        String apiPath = mount + "/cert/" + request.serialNumber();

        PkiFormat format = request.format();
        if (format != PkiFormat.PEM) {
            throw new UnsupportedOperationException("Only PEM encoded format is supported");
        }

        return restApi.get(apiPath, request, VaultOptionalResponse.<CertificateGet.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(CertificateGet.Response::create));
    }

    @Override
    public Single<CrlGet.Response> crl(CrlGet.Request request) {
        PkiFormat format = request.format();

        String apiPath;
        switch (format) {
        case DER:
            apiPath = mount + "/crl";
            break;
        case PEM:
        case PEM_BUNDLE:
            apiPath = mount + "/crl/pem";
            break;
        default:
            return Single.error(new VaultApiException("Unsupported PKI Format: " + format));
        }

        return restApi.getBytes(apiPath,
                                request,
                                VaultOptionalResponse.<byte[], byte[]>vaultResponseBuilder()
                                        .entityProcessor(Function.identity()))
                .map(optionalResponse -> CrlGet.Response.builder()
                        .entity(optionalResponse.entity().orElseThrow(() -> VaultRestException.builder()
                                .headers(optionalResponse.headers())
                                .requestId(optionalResponse.requestId())
                                .status(optionalResponse.status())
                                .vaultErrors(optionalResponse.errors())
                                .message("CRL is expected to be present, but is not available")
                                .build()))
                        .headers(optionalResponse.headers())
                        .requestId(optionalResponse.requestId())
                        .status(optionalResponse.status())
                        .build());
    }

    @Override
    public Single<IssueCertificate.Response> issueCertificate(IssueCertificate.Request request) {
        String apiPath = mount + "/issue/" + request.roleName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, IssueCertificate.Response.builder()
                .format(request.format()));
    }

    @Override
    public Single<SignCsr.Response> signCertificateRequest(SignCsr.Request request) {
        String apiPath = mount + "/sign/" + request.roleName();

        return restApi.invokeWithResponse(Http.Method.POST,
                                          apiPath,
                                          request,
                                          SignCsr.Response.builder()
                                                  .format(request.format()));
    }

    @Override
    public Single<RevokeCertificate.Response> revokeCertificate(RevokeCertificate.Request request) {
        String apiPath = mount + "/revoke";

        return restApi.invokeWithResponse(Http.Method.POST,
                                          apiPath,
                                          request,
                                          RevokeCertificate.Response.builder());
    }

    @Override
    public Single<GenerateSelfSignedRoot.Response> generateSelfSignedRoot(GenerateSelfSignedRoot.Request request) {
        String apiPath = mount + "/root/generate/internal";

        return restApi.post(apiPath, request, GenerateSelfSignedRoot.Response.builder());
    }

    @Override
    public Single<PkiRole.Response> createOrUpdateRole(PkiRole.Request request) {
        String apiPath = mount + "/roles/" + request.roleName();

        return restApi.post(apiPath, request, PkiRole.Response.builder());
    }
}
