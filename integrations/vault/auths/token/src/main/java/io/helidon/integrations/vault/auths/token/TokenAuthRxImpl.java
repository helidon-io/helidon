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

package io.helidon.integrations.vault.auths.token;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.RestApi;

class TokenAuthRxImpl implements TokenAuthRx {
    private final RestApi restApi;
    private final String path;

    TokenAuthRxImpl(RestApi restApi, String path) {
        this.restApi = restApi;
        this.path = path;
    }

    @Override
    public Single<CreateToken.Response> createToken(CreateToken.Request request) {
        String apiPath = "/auth/" + path + "/create" + request.roleName().map(it -> "/" + it).orElse("");

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, CreateToken.Response.builder());
    }

    @Override
    public Single<RenewToken.Response> renew(RenewToken.Request request) {
        String apiPath = "/auth/" + path + "/renew";

        return restApi.invokeWithResponse(Http.Method.POST, apiPath, request, RenewToken.Response.builder());
    }

    @Override
    public Single<RevokeToken.Response> revoke(RevokeToken.Request request) {
        String apiPath = "/auth/" + path + "/revoke";

        return restApi.post(apiPath, request, RevokeToken.Response.builder());
    }

    @Override
    public Single<CreateTokenRole.Response> createTokenRole(CreateTokenRole.Request request) {
        String apiPath = "/auth/" + path + "/roles/" + request.roleName();

        return restApi.post(apiPath, request, CreateTokenRole.Response.builder());
    }

    @Override
    public Single<DeleteTokenRole.Response> deleteTokenRole(DeleteTokenRole.Request request) {
        String apiPath = "/auth/" + path + "/roles/" + request.roleName();

        return restApi.delete(apiPath, request, DeleteTokenRole.Response.builder());
    }

    @Override
    public Single<RevokeAndOrphanToken.Response> revokeAndOrphan(RevokeAndOrphanToken.Request request) {
        String apiPath = "/auth/" + path + "/revoke-orphan";

        return restApi.post(apiPath, request, RevokeAndOrphanToken.Response.builder());
    }
}
