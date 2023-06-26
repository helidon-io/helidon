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

package io.helidon.integrations.vault.secrets.cubbyhole;

import java.util.Optional;

import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Secret;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultApiException;
import io.helidon.integrations.vault.VaultOptionalResponse;
import io.helidon.integrations.vault.VaultRestException;

import jakarta.json.JsonObject;

class CubbyholeSecretsImpl implements CubbyholeSecrets {
    private final RestApi restApi;
    private final String mount;

    CubbyholeSecretsImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public VaultOptionalResponse<GetCubbyhole.Response> get(GetCubbyhole.Request request) {
        String path = request.path();
        String apiPath = mount + "/" + path;

        return restApi.get(apiPath, request, VaultOptionalResponse.<GetCubbyhole.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(json -> GetCubbyhole.Response.create(path, json)));
    }

    @Override
    public VaultOptionalResponse<ListSecrets.Response> list(ListSecrets.Request request) {
        String apiPath = mount + "/" + request.path().orElse("");

        return restApi
                .invokeOptional(Vault.LIST,
                                apiPath,
                                request,
                                VaultOptionalResponse.<ListSecrets.Response, JsonObject>vaultResponseBuilder()
                                        .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public CreateCubbyhole.Response create(CreateCubbyhole.Request request) throws VaultRestException {
        String path = request.path();

        Optional<Secret> secret = get(path);
        if (secret.isPresent()) {
            throw new VaultApiException(
                    "Cannot create a secret that already exists on path: \"%s\", please use update",
                    path);
        }
        String apiPath = mount + "/" + path;
        return restApi.post(apiPath, request, CreateCubbyhole.Response.builder());
    }

    @Override
    public UpdateCubbyhole.Response update(UpdateCubbyhole.Request request) {
        String path = request.path();

        Optional<Secret> secret = get(path);
        if (secret.isEmpty()) {
            throw new VaultApiException(
                    "Cannot update a secret that does not exist on path: \"%s\", please use create",
                    path);
        }
        String apiPath = mount + "/" + path;
        return restApi.put(apiPath, request, UpdateCubbyhole.Response.builder());
    }

    @Override
    public DeleteCubbyhole.Response delete(DeleteCubbyhole.Request request) {
        String apiPath = mount + "/" + request.path();
        return restApi.delete(apiPath, request, DeleteCubbyhole.Response.builder());
    }
}
