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

package io.helidon.integrations.oci.atp;

import java.util.Optional;

import io.helidon.common.http.Http;
import io.helidon.common.reactive.Single;
import io.helidon.integrations.common.rest.ApiOptionalResponse;
import io.helidon.integrations.oci.connect.OciRestApi;

class OciAutonomousDbRxImpl implements OciAutonomousDbRx {
    private final OciRestApi restApi;
    private final String hostPrefix;
    private final Optional<String> endpoint;
    private final String ocid;
    private final String walletPassword;

    OciAutonomousDbRxImpl(Builder builder) {
        this.restApi = builder.restApi();
        this.hostPrefix = builder.hostPrefix();
        this.endpoint = Optional.ofNullable(builder.endpoint());
        this.ocid = builder.ocid();
        this.walletPassword = builder.walletPassword();
    }

    @Override
    public Single<ApiOptionalResponse<GenerateAutonomousDatabaseWallet.Response>> generateWallet(
            GenerateAutonomousDatabaseWallet.Request request) {
        String apiPath = "/20160918/autonomousDatabases/" + this.ocid + "/actions/generateWallet";

        if (!request.endpoint().isPresent()) {
            endpoint.ifPresent(request::endpoint);
            request.hostFormat(OciAutonomousDbRx.API_HOST_FORMAT)
                    .hostPrefix(hostPrefix);
        }

        request.password(this.walletPassword);

        return restApi.invokeBytesResponse(Http.Method.POST,
                apiPath,
                request,
                ApiOptionalResponse.<byte[], GenerateAutonomousDatabaseWallet.Response>apiResponseBuilder()
                                .entityProcessor(GenerateAutonomousDatabaseWallet.Response::create));
    }
}
