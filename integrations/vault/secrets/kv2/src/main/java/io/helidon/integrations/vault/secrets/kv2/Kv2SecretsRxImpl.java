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

package io.helidon.integrations.vault.secrets.kv2;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultOptionalResponse;

class Kv2SecretsRxImpl implements Kv2SecretsRx {

    private final RestApi restApi;
    private final String mount;

    Kv2SecretsRxImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request) {
        String apiPath = mount + "/metadata/" + request.path().orElse("");

        return restApi
                .invokeOptional(Vault.LIST,
                                apiPath,
                                request,
                                VaultOptionalResponse.<ListSecrets.Response, JsonObject>vaultResponseBuilder()
                                        .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public Single<VaultOptionalResponse<GetKv2.Response>> get(GetKv2.Request request) {
        String path = request.path();
        String apiPath = mount + "/data/" + path;

        return restApi.get(apiPath, request, VaultOptionalResponse.<GetKv2.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(json -> GetKv2.Response.create(path, json)));
    }

    @Override
    public Single<UpdateKv2.Response> update(UpdateKv2.Request request) {
        String path = request.path();

        return get(path)
                .flatMapSingle(secret -> {
                    if (secret.isEmpty()) {
                        return Single.error(new VaultApiException("Cannot update a secret that does not exist on path: \""
                                                                          + path
                                                                          + "\", please use create"));
                    }
                    String apiPath = mount + "/data/" + path;

                    return restApi.invokeWithResponse(Http.Method.POST,
                                                      apiPath,
                                                      request,
                                                      UpdateKv2.Response.builder());
                });
    }

    @Override
    public Single<CreateKv2.Response> create(CreateKv2.Request request) {
        String apiPath = mount + "/data/" + request.path();

        return restApi.post(apiPath, request, CreateKv2.Response.builder());
    }

    @Override
    public Single<DeleteKv2.Response> delete(DeleteKv2.Request request) {
        String apiPath = mount + "/delete/" + request.path();

        return restApi.post(apiPath, request, DeleteKv2.Response.builder());
    }

    @Override
    public Single<UndeleteKv2.Response> undelete(UndeleteKv2.Request request) {
        String apiPath = mount + "/undelete/" + request.path();

        return restApi.post(apiPath, request, UndeleteKv2.Response.builder());
    }

    @Override
    public Single<DestroyKv2.Response> destroy(DestroyKv2.Request request) {
        String apiPath = mount + "/destroy/" + request.path();

        return restApi.post(apiPath, request, DestroyKv2.Response.builder());
    }

    @Override
    public Single<DeleteAllKv2.Response> deleteAll(DeleteAllKv2.Request request) {
        String apiPath = mount + "/metadata/" + request.path();

        return restApi.delete(apiPath, request, DeleteAllKv2.Response.builder());
    }
}
