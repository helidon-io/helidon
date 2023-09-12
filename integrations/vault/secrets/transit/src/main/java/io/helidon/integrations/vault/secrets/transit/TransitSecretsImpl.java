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

package io.helidon.integrations.vault.secrets.transit;

import io.helidon.http.Method;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultOptionalResponse;

import jakarta.json.JsonObject;

class TransitSecretsImpl implements TransitSecrets {
    private final RestApi restApi;
    private final String mount;

    TransitSecretsImpl(RestApi restApi, String mount) {
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
    public CreateKey.Response createKey(CreateKey.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name();

        return restApi.post(apiPath, request, CreateKey.Response.builder());
    }

    @Override
    public DeleteKey.Response deleteKey(DeleteKey.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name();

        return restApi.delete(apiPath, request, DeleteKey.Response.builder());
    }

    @Override
    public UpdateKeyConfig.Response updateKeyConfig(UpdateKeyConfig.Request request) {
        String apiPath = "/" + mount + "/keys/" + request.name() + "/config";

        return restApi.post(apiPath, request, UpdateKeyConfig.Response.builder());
    }

    @Override
    public Encrypt.Response encrypt(Encrypt.Request request) {
        String apiPath = "/" + mount + "/encrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Encrypt.Response.builder());
    }

    @Override
    public EncryptBatch.Response encrypt(EncryptBatch.Request request) {
        String apiPath = "/" + mount + "/encrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, EncryptBatch.Response.builder());
    }

    @Override
    public Decrypt.Response decrypt(Decrypt.Request request) {
        String apiPath = "/" + mount + "/decrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Decrypt.Response.builder());
    }

    @Override
    public DecryptBatch.Response decrypt(DecryptBatch.Request request) {
        String apiPath = "/" + mount + "/decrypt/" + request.encryptionKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, DecryptBatch.Response.builder());
    }

    @Override
    public Hmac.Response hmac(Hmac.Request request) {
        String apiPath = "/" + mount + "/hmac/" + request.hmacKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Hmac.Response.builder());
    }

    @Override
    public Sign.Response sign(Sign.Request request) {
        String apiPath = "/" + mount + "/sign/" + request.signatureKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Sign.Response.builder());
    }

    @Override
    public Verify.Response verify(Verify.Request request) {
        String apiPath = "/" + mount + "/verify/" + request.digestKeyName();

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Verify.Response.builder());
    }
}
