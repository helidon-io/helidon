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

package io.helidon.integrations.vault.secrets.database;

import javax.json.JsonObject;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.ListSecrets;
import io.helidon.integrations.vault.Vault;
import io.helidon.integrations.vault.VaultOptionalResponse;

class DbSecretsRxImpl implements DbSecretsRx {
    private final RestApi restApi;
    private final String mount;

    DbSecretsRxImpl(RestApi restApi, String mount) {
        this.restApi = restApi;
        this.mount = mount;
    }

    @Override
    public Single<VaultOptionalResponse<ListSecrets.Response>> list(ListSecrets.Request request) {
        String apiPath = mount + "/config";

        return restApi.invokeOptional(Vault.LIST,
                                      apiPath,
                                      request,
                                      VaultOptionalResponse.<ListSecrets.Response, JsonObject>vaultResponseBuilder()
                                              .entityProcessor(ListSecrets.Response::create));
    }

    @Override
    public Single<VaultOptionalResponse<DbGet.Response>> get(DbGet.Request request) {
        String name = request.name();
        String apiPath = mount + "/creds/" + name;

        return restApi.get(apiPath, request, VaultOptionalResponse.<DbGet.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(json -> DbGet.Response.create(name, json)));
    }

    @Override
    public Single<DbCreateRole.Response> createRole(DbCreateRole.Request request) {
        String apiPath = mount + "/roles/" + request.name();

        return restApi.post(apiPath, request, DbCreateRole.Response.builder());
    }

    @Override
    public Single<DbConfigure.Response> configure(DbConfigure.Request<?> dbRequest) {
        String apiPath = mount + "/config/" + dbRequest.name();

        return restApi.post(apiPath, dbRequest, DbConfigure.Response.builder());
    }

    @Override
    public Single<DbDelete.Response> delete(DbDelete.Request request) {
        String apiPath = mount + "/config/" + request.name();
        return restApi.delete(apiPath, request, DbDelete.Response.builder());
    }

    @Override
    public Single<DbDeleteRole.Response> deleteRole(DbDeleteRole.Request request) {
        String apiPath = mount + "/roles/" + request.name();

        return restApi.delete(apiPath, request, DbDeleteRole.Response.builder());
    }
}
