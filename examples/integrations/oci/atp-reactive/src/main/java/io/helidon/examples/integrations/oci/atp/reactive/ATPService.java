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

package io.helidon.examples.integrations.oci.atp.reactive;

import java.util.Optional;
import java.util.OptionalLong;

import io.helidon.common.http.DataChunk;
import io.helidon.common.http.Http;
import io.helidon.integrations.oci.atp.OciAutonomousDBRx;
import io.helidon.integrations.oci.atp.GenerateAutonomousDatabaseWalletRx;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

class ATPService implements Service {
    private final OciAutonomousDBRx atpDB;

    ATPService(OciAutonomousDBRx atpDB) {
        this.atpDB = atpDB;
    }

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/wallet", this::generateWallet);
    }

    private void generateWallet(ServerRequest req, ServerResponse res) {
        atpDB.generateWallet(GenerateAutonomousDatabaseWalletRx.Request.builder())
                .forSingle(apiResponse -> {
                    Optional<GenerateAutonomousDatabaseWalletRx.Response> entity = apiResponse.entity();
                    if (entity.isEmpty()) {
                        res.status(Http.Status.NOT_FOUND_404).send();
                    } else {
                        GenerateAutonomousDatabaseWalletRx.Response response = entity.get();
                        // copy the content length header to response
                        apiResponse.headers()
                                .first(Http.Header.CONTENT_LENGTH)
                                .ifPresent(res.headers()::add);
                        res.send(response.publisher());
                    }
                })
                .exceptionally(res::send);
    }
}
