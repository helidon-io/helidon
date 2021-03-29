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

package io.helidon.integrations.vault.secrets.kv1;

import javax.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultOptionalResponse;

class Kv1SecretsImpl implements Kv1Secrets {
    private final RestApi restApi;
    private final String mount;

    Kv1SecretsImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request) {
        String apiPath = mount + "/" + request.path().orElse("");

        return restApi
                .invokeOptional(Vault.LIST, apiPath, request, VaultOptionalResponse.<ListSecrets.Response, JsonObject>builder()
                        .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public Single<VaultOptionalResponse<GetKv1.Response>> get(GetKv1.Request request) {
        String path = request.path();
        String apiPath = mount + "/" + path;

        return restApi.get(apiPath, request, VaultOptionalResponse.<GetKv1.Response, JsonObject>builder()
                .entityProcessor(it -> GetKv1.Response.create(path, it)));
    }

    @Override
    public Single<CreateKv1.Response> create(CreateKv1.Request request) {
        String path = request.path();

        return get(path)
                .flatMapSingle(secret -> {
                    if (secret.isPresent()) {
                        return Single.error(new VaultApiException("Cannot create a secret that already exists on path: \""
                                                                          + path
                                                                          + "\", please use update"));
                    }
                    String apiPath = mount + "/" + path;

                    return restApi.post(apiPath, request, CreateKv1.Response.builder());
                });
    }

    @Override
    public Single<UpdateKv1.Response> update(UpdateKv1.Request request) {
        String path = request.path();

        return get(path)
                .flatMapSingle(secret -> {
                    if (secret.isEmpty()) {
                        return Single.error(new VaultApiException("Cannot update a secret that does not exist on path: \""
                                                                          + path
                                                                          + "\", please use create"));
                    }
                    String apiPath = mount + "/" + path;

                    return restApi.put(apiPath, request, UpdateKv1.Response.builder());
                });
    }

    @Override
    public Single<DeleteKv1.Response> delete(DeleteKv1.Request request) {
        String apiPath = mount + "/" + request.path();

        return restApi.delete(apiPath, request, DeleteKv1.Response.builder());
    }
}
