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

package io.helidon.integrations.vault.auths.k8s;

import io.helidon.http.Method;
import io.helidon.integrations.common.rest.RestApi;

class K8sAuthImpl implements K8sAuth {
    private final RestApi restApi;
    private final String path;

    K8sAuthImpl(RestApi restApi, String path) {
        this.restApi = restApi;
        this.path = path;
    }

    @Override
    public CreateRole.Response createRole(CreateRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.post(apiPath, request, CreateRole.Response.builder());
    }

    @Override
    public DeleteRole.Response deleteRole(DeleteRole.Request request) {
        String apiPath = "/auth/" + path + "/role/" + request.roleName();

        return restApi.delete(apiPath, request, DeleteRole.Response.builder());
    }

    @Override
    public ConfigureK8s.Response configure(ConfigureK8s.Request request) {
        String apiPath = "/auth/" + path + "/config";

        return restApi.post(apiPath, request, ConfigureK8s.Response.builder());
    }

    @Override
    public Login.Response login(Login.Request request) {
        String apiPath = "/auth/" + path + "/login";

        return restApi.invokeWithResponse(Method.POST, apiPath, request, Login.Response.builder());
    }
}
