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

package io.helidon.integrations.vault.secrets.transit;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultOptionalResponse;

class TransitSecretsImpl implements TransitSecrets {
    private final RestApi restApi;
    private final String mount;

    TransitSecretsImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request) {
        String apiPath = mount + "/certs";

        return restApi
                .invokeOptional(Vault.LIST, apiPath, request, VaultOptionalResponse.<ListSecrets.Response, JsonObject>builder()
                        .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public Single<CreateKey.Response> createKey(CreateKey.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name();

        return restApi.post(apiPath, request, CreateKey.Response.builder());
    }

    @Override
    public Single<DeleteKey.Response> deleteKey(DeleteKey.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name();

        return restApi.delete(apiPath, request, DeleteKey.Response.builder());
    }

    @Override
    public Single<UpdateKeyConfig.Response> updateKeyConfig(UpdateKeyConfig.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name() + "/config";

        return restApi.post(apiPath, request, UpdateKeyConfig.Response.builder());
    }

    @Override
    public Single<Encrypt.Response> encrypt(Encrypt.Request request) {
        String apiPath = "/" + mount + "/encrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, Encrypt.Response.builder());
    }

    @Override
    public Single<EncryptBatch.Response> encrypt(EncryptBatch.Request request) {
        String apiPath = "/" + mount + "/encrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, EncryptBatch.Response.builder());
    }

    @Override
    public Single<Decrypt.Response> decrypt(Decrypt.Request request) {
        String apiPath = "/" + mount + "/decrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, Decrypt.Response.builder());
    }

    @Override
    public Single<DecryptBatch.Response> decrypt(DecryptBatch.Request request) {
        String apiPath = "/" + mount + "/decrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, DecryptBatch.Response.builder());
    }

    @Override
    public Single<Hmac.Response> hmac(Hmac.Request request) {
        String apiPath = "/" + mount + "/hmac/" + request.hmacKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, Hmac.Response.builder());
    }

    @Override
    public Single<Sign.Response> sign(Sign.Request request) {
        String apiPath = "/" + mount + "/sign/" + request.signatureKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, Sign.Response.builder());
    }

    @Override
    public Single<Verify.Response> verify(Verify.Request request) {
        String apiPath = "/" + mount + "/verify/" + request.digestKeyName();

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, Verify.Response.builder());
    }
}
