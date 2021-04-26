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

package io.helidon.integrations.vault.sys;

import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;

class SysRxImpl implements SysRx {
    private final RestApi restApi;

    SysRxImpl(RestApi restApi) {
        this.restApi = restApi;
    }

    @Override
    public Single<EnableEngine.Response> enableEngine(EnableEngine.Request request) {
        String apiPath = "/sys/mounts/" + request.path();

        return restApi.post(apiPath, request, EnableEngine.Response.builder());
    }

    @Override
    public Single<DisableEngine.Response> disableEngine(DisableEngine.Request request) {
        String apiPath = "/sys/mounts/" + request.path();

        return restApi.delete(apiPath, request, DisableEngine.Response.builder());
    }

    @Override
    public Single<EnableAuth.Response> enableAuth(EnableAuth.Request request) {
        String apiPath = "/sys/auth/" + request.path();

        return restApi.post(apiPath, request, EnableAuth.Response.builder());
    }

    @Override
    public Single<DisableAuth.Response> disableAuth(DisableAuth.Request request) {
        String apiPath = "/sys/auth/" + request.path();

        return restApi.delete(apiPath, request, DisableAuth.Response.builder());
    }

    @Override
    public Single<CreatePolicy.Response> createPolicy(CreatePolicy.Request request) {
        String apiPath = "/sys/policy/" + request.name();

        return restApi.put(apiPath, request, CreatePolicy.Response.builder());
    }

    @Override
    public Single<DeletePolicy.Response> deletePolicy(DeletePolicy.Request request) {
        String apiPath = "/sys/policy/" + request.name();

        return restApi.delete(apiPath, request, DeletePolicy.Response.builder());
    }
}
