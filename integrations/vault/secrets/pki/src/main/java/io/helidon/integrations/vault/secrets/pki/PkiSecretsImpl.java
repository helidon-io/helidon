/*
 * Copyright (c) 2021, 2023 Oracle and/or its affiliates.
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

import io.helidon.http.Method;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.integrations.vault.VaultRestException;

import jakarta.json.JsonObject;

class PkiSecretsImpl implements PkiSecrets {
    private final RestApi restApi;
    private final String mount;

    PkiSecretsImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        String apiPath = mount + "/certs";

        return restApi.invokeOptional(Vault.LIST,
                                      apiPath,
                                      request,
                                      VaultOptionalResponse.<ListSecrets.Response, JsonObject>vaultResponseBuilder()
                                              .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public CaCertificateGet.Response caCertificate(CaCertificateGet.Request request) {
        String apiPath = switch (request.format()) {
            case DER -> mount + "/ca";
            case PEM, PEM_BUNDLE -> mount + "/ca_chain";
        };

        VaultOptionalResponse<byte[]> response = restApi.getBytes(apiPath,
                                                                  request,
                                                                  VaultOptionalResponse.<byte[], byte[]>vaultResponseBuilder()
                                                                          .entityProcessor(Function.identity()));
        return CaCertificateGet.Response.builder()
                .entity(response.entity()
                                .orElseThrow(() -> VaultRestException.builder()
                                        .headers(response.headers())
                                        .requestId(response.requestId())
                                        .status(response.status())
                                        .vaultErrors(response.errors())
                                        .message(
                                                "CA Certificate is expected to be present, but is"
                                                        + " not available")
                                        .build()))
                .headers(response.headers())
                .requestId(response.requestId())
                .status(response.status())
                .build();
    }

    @Override
    public VaultOptionalResponse<CertificateGet.Response> certificate(CertificateGet.Request request) {
        String apiPath = mount + "/cert/" + request.serialNumber();

        PkiFormat format = request.format();
        if (format != PkiFormat.PEM) {
            throw new UnsupportedOperationException("Only PEM encoded format is supported");
        }

        return restApi.get(apiPath, request, VaultOptionalResponse.<CertificateGet.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(CertificateGet.Response::create));
    }

    @Override
    public CrlGet.Response crl(CrlGet.Request request) {
        PkiFormat format = request.format();

        String apiPath = switch (format) {
            case DER -> mount + "/crl";
            case PEM, PEM_BUNDLE -> mount + "/crl/pem";
        };

        VaultOptionalResponse<byte[]> response = restApi.getBytes(apiPath,
                                                                  request,
                                                                  VaultOptionalResponse.<byte[], byte[]>vaultResponseBuilder()
                                                                          .entityProcessor(Function.identity()));
        return CrlGet.Response.builder()
                .entity(response.entity()
                                .orElseThrow(() -> VaultRestException.builder()
                                        .headers(response.headers())
                                        .requestId(response.requestId())
                                        .status(response.status())
                                        .vaultErrors(response.errors())
                                        .message(
                                                "CRL is expected to be present, but is not "
                                                        + "available")
                                        .build()))
                .headers(response.headers())
                .requestId(response.requestId())
                .status(response.status())
                .build();
    }

    @Override
    public IssueCertificate.Response issueCertificate(IssueCertificate.Request request) {
        String apiPath = mount + "/issue/" + request.roleName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request,
                                          IssueCertificate.Response.builder()
                                                  .format(request.format()));
    }

    @Override
    public SignCsr.Response signCertificateRequest(SignCsr.Request request) {
        String apiPath = mount + "/sign/" + request.roleName();

        return restApi.invokeWithResponse(Method.POST,
                                          apiPath,
                                          request,
                                          SignCsr.Response.builder()
                                                  .format(request.format()));
    }

    @Override
    public RevokeCertificate.Response revokeCertificate(RevokeCertificate.Request request) {
        String apiPath = mount + "/revoke";

        return restApi.invokeWithResponse(Method.POST,
                                          apiPath,
                                          request,
                                          RevokeCertificate.Response.builder());
    }

    @Override
    public GenerateSelfSignedRoot.Response generateSelfSignedRoot(GenerateSelfSignedRoot.Request request) {
        String apiPath = mount + "/root/generate/internal";

        return restApi.post(apiPath, request, GenerateSelfSignedRoot.Response.builder());
    }

    @Override
    public PkiRole.Response createOrUpdateRole(PkiRole.Request request) {
        String apiPath = mount + "/roles/" + request.roleName();

        return restApi.post(apiPath, request, PkiRole.Response.builder());
    }
}
