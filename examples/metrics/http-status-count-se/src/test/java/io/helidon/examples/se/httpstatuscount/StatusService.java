/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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
package io.helidon.examples.se.httpstatuscount;

import io.helidon.common.http.Http;
import io.helidon.webserver.Routing;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;
import io.helidon.webserver.Service;

/**
 * Test-only service that allows the client to specify what HTTP status the service should return in its response.
 * This allows the client to know which status family counter should be updated.
 */
public class StatusService implements Service {

    @Override
    public void update(Routing.Rules rules) {
        rules.get("/{status}", this::respondWithRequestedStatus);
    }

    private void respondWithRequestedStatus(ServerRequest request, ServerResponse response) {
        String statusText = request.path().param("status");
        int status;
        String msg;
        try {
            status = Integer.parseInt(statusText);
            msg = "Successful conversion";
        } catch (NumberFormatException ex) {
            status = Http.Status.INTERNAL_SERVER_ERROR_500.code();
            msg = "Unsuccessful conversion";
        }
        response.status(status)
                        .send(msg);
    }
}
