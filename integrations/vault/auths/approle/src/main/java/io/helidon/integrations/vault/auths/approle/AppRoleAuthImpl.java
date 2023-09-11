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

package io.helidon.integrations.vault.auths.approle;

import io.helidon.http.Method;
import io.helidon.integrations.common.rest.RestApi;
import io.helidon.integrations.vault.VaultOptionalResponse;

import jakarta.json.JsonObject;

class AppRoleAuthImpl implements AppRoleAuth {
    private final RestApi restApi;
    private final String path;

    AppRoleAuthImpl(RestApi restApi, String path) {
        this.restApi = restApi;
        this.path = path;
    }

    @Override
    public CreateAppRole.Response createAppRole(CreateAppRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.post(apiPath, request, CreateAppRole.Response.builder());
    }

    @Override
    public DeleteAppRole.Response deleteAppRole(DeleteAppRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.delete(apiPath, request, DeleteAppRole.Response.builder());
    }

    @Override
    public VaultOptionalResponse<ReadRoleId.Response> readRoleId(ReadRoleId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/role-id";

        return restApi.get(apiPath, request, VaultOptionalResponse.<ReadRoleId.Response, JsonObject>vaultResponseBuilder()
                .entityProcessor(ReadRoleId.Response::create));
    }

    @Override
    public GenerateSecretId.Response generateSecretId(GenerateSecretId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/secret-id";

        return restApi.invokeWithResponse(Method.POST, apiPath, request, GenerateSecretId.Response.builder());
    }

    @Override
    public DestroySecretId.Response destroySecretId(DestroySecretId.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName() + "/secret-id/destroy";

        return restApi.post(apiPath, request, DestroySecretId.Response.builder());
    }

    @Override
    public Login.Response login(Login.Request request) {
        String apiPath = "/auth/" + path + "/login";

        return restApi.invokeWithResponse(Method.POST,
                                          apiPath,
                                          request,
                                          Login.Response.builder());
    }
}
