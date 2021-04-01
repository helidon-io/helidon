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

package io.helidon.integrations.vault.auths.approle;

import javax.json.JsonObject;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.VaultOptionalResponse;

class AppRoleAuthRxImpl implements AppRoleAuthRx {
    private final RestApi restApi;
    private final String path;

    AppRoleAuthRxImpl(RestApi restApi, String path) {
        this.restApi = restApi;
        this.path = path;
    }

    @Override
    public Single<CreateAppRole.Response> createAppRole(CreateAppRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.post(apiPath, request, CreateAppRole.Response.builder());
    }

    @Override
    public Single<DeleteAppRole.Response> deleteAppRole(DeleteAppRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.delete(apiPath, request, DeleteAppRole.Response.builder());
    }

    @Override
    public Single<VaultOptionalResponse<ReadRoleId.Response>> readRoleId(ReadRoleId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/role-id";

        return restApi.get(apiPath, request, VaultOptionalResponse.<ReadRoleId.Response, JsonObject>builder()
                .entityProcessor(ReadRoleId.Response::create));
    }

    @Override
    public Single<GenerateSecretId.Response> generateSecretId(GenerateSecretId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/secret-id";

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, GenerateSecretId.Response.builder());
    }

    @Override
    public Single<DestroySecretId.Response> destroySecretId(DestroySecretId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/secret-id/destroy";

        return restApi.post(apiPath, request, DestroySecretId.Response.builder());
    }

    @Override
    public Single<Login.Response> login(Login.Request request) {
        String apiPath = "/auth/" + path + "/login";

        return restApi.invokeWithResponse(Http.Method.POST,
                                          apiPath,
                                          request,
                                          Login.Response.builder());
    }
}
