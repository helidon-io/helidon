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

package io.helidon.integrations.oci.vault;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciApiException;
import io.helidon.integrations.oci.connect.OciRestApi;

class OciVaultImpl implements OciVault {
    private final String secretApiVersion;
    private final String bundleApiVersion;
    private final OciRestApi restAccess;
    private final String cryptographicEndpoint;

    OciVaultImpl(Builder builder) {
        this.restAccess = builder.restAccess();
        this.secretApiVersion = builder.secretApiVersion();
        this.bundleApiVersion = builder.secretBundleApiVersion();
        this.cryptographicEndpoint = builder.cryptographicEndpoint();
    }

    @Override
    public Single<ApiOptionalResponse<Secret>> getSecret(GetSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets/" + request.secretId();

        return restAccess.get(apiPath,
                              request.hostPrefix(OciVault.VAULTS_HOST_PREFIX),
                              ApiOptionalResponse.<JsonObject, Secret>apiResponseBuilder()
                                      .entityProcessor(Secret::create));
    }

    @Override
    public Single<CreateSecret.Response> createSecret(CreateSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets";

        return restAccess.invokeWithResponse(Http.Method.POST,
                                             apiPath,
                                             request.hostPrefix(OciVault.VAULTS_HOST_PREFIX),
                                             CreateSecret.Response.builder());
    }

    @Override
    public Single<ApiOptionalResponse<GetSecretBundle.Response>> getSecretBundle(GetSecretBundle.Request request) {
        String apiPath = bundleApiVersion + "/secretbundles/" + request.secretId();

        return restAccess.get(apiPath,
                              request.hostPrefix(OciVault.RETRIEVAL_HOST_PREFIX),
                              ApiOptionalResponse.<JsonObject, GetSecretBundle.Response>apiResponseBuilder()
                                      .entityProcessor(GetSecretBundle.Response::create));
    }

    @Override
    public Single<DeleteSecret.Response> deleteSecret(DeleteSecret.Request request) {
        String apiPath = secretApiVersion + "/secrets/" + request.secretId() + "/actions/scheduleDeletion";

        return restAccess.post(apiPath,
                               request.hostPrefix(OciVault.VAULTS_HOST_PREFIX),
                               DeleteSecret.Response.builder());
    }

    @Override
    public Single<Encrypt.Response> encrypt(Encrypt.Request request) {
        String apiPath = secretApiVersion + "/encrypt";

        if (cryptographicEndpoint == null) {
            return Single
                    .error(new OciApiException("Cryptographic endpoint is not configured, it is available on Vault overview "
                                                       + "page"));
        }

        return restAccess.invokeWithResponse(Http.Method.POST,
                                             apiPath,
                                             request.address(cryptographicEndpoint),
                                             Encrypt.Response.builder());
    }

    @Override
    public Single<Decrypt.Response> decrypt(Decrypt.Request request) {
        String apiPath = secretApiVersion + "/decrypt";
        if (cryptographicEndpoint == null) {
            return Single
                    .error(new OciApiException("Cryptographic endpoint is not configured, it is available on Vault overview "
                                                       + "page"));
        }
        return restAccess.invokeWithResponse(Http.Method.POST,
                                             apiPath,
                                             request.address(cryptographicEndpoint),
                                             Decrypt.Response.builder());
    }

    @Override
    public Single<Sign.Response> sign(Sign.Request request) {
        if (cryptographicEndpoint == null) {
            return Single
                    .error(new OciApiException("Cryptographic endpoint is not configured, it is available on Vault overview "
                                                       + "page"));
        }
        String apiPath = secretApiVersion + "/sign";

        return restAccess.invokeWithResponse(Http.Method.POST,
                                             apiPath,
                                             request.address(cryptographicEndpoint),
                                             Sign.Response.builder());
    }

    @Override
    public Single<Verify.Response> verify(Verify.Request request) {
        if (cryptographicEndpoint == null) {
            return Single
                    .error(new OciApiException("Cryptographic endpoint is not configured, it is available on Vault overview "
                                                       + "page"));
        }

        String apiPath = secretApiVersion + "/verify";

        return restAccess.invokeWithResponse(Http.Method.POST,
                                             apiPath,
                                             request.address(cryptographicEndpoint),
                                             Verify.Response.builder());
    }
}
