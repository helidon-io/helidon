/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.health;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.http.HeaderValues;
import io.helidon.http.HtmlEncoder;
import io.helidon.http.Status;
import io.helidon.http.media.EntityWriter;
import io.helidon.http.media.jsonp.JsonpSupport;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;

class HealthHandler implements Handler {
    private static final System.Logger LOGGER = System.getLogger(HealthHandler.class.getName());

    private final EntityWriter<JsonObject> entityWriter;
    private final boolean details;
    private final Collection<HealthCheck> checks;

    HealthHandler(EntityWriter<JsonObject> entityWriter,
                  boolean details,
                  List<HealthCheck> checks) {
        this.entityWriter = entityWriter;
        this.details = details;
        this.checks = checks;
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        List<NamedResponse> responses = new ArrayList<>();
        HealthCheckResponse.Status status = HealthCheckResponse.Status.UP;

        for (HealthCheck check : checks) {
            HealthCheckResponse response;

            try {
                response = check.call();
            } catch (Exception e) {
                response = HealthCheckResponse.builder()
                        .status(HealthCheckResponse.Status.ERROR)
                        .detail("error", e.getClass().getName())
                        .detail("message", HtmlEncoder.encode(e.getMessage()))
                        .build();
                LOGGER.log(System.Logger.Level.ERROR, "Unexpected failure of health check", e);
            }
            // we may have more checks with the same name (such as in MP Health)
            responses.add(new NamedResponse(check.name(), response));

            if (response.status() == HealthCheckResponse.Status.ERROR) {
                status = HealthCheckResponse.Status.ERROR;
            } else if (response.status() == HealthCheckResponse.Status.DOWN && status == HealthCheckResponse.Status.UP) {
                status = HealthCheckResponse.Status.DOWN;
            }
        }

        Status responseStatus = switch (status) {
            case UP -> details ? Status.OK_200 : Status.NO_CONTENT_204;
            case DOWN -> Status.SERVICE_UNAVAILABLE_503;
            case ERROR -> Status.INTERNAL_SERVER_ERROR_500;
        };

        res.status(responseStatus);
        res.header(HeaderValues.CACHE_NO_CACHE)
                .header(HeaderValues.X_CONTENT_TYPE_OPTIONS_NOSNIFF);

        if (details) {
            entityWriter.write(JsonpSupport.JSON_OBJECT_TYPE,
                               toJson(status, responses),
                               res.outputStream(),
                               req.headers(),
                               res.headers());

        } else {
            res.send();
        }
    }

    private static JsonObject toJson(HealthCheckResponse.Status status, List<NamedResponse> responses) {
        JsonObjectBuilder response = HealthHelper.JSON.createObjectBuilder();
        response.add("status", status.toString());

        JsonArrayBuilder checks = HealthHelper.JSON.createArrayBuilder();
        responses.forEach(result -> checks.add(HealthHelper.toJson(result.name(), result.response())));

        response.add("checks", checks);
        return response.build();
    }
}
