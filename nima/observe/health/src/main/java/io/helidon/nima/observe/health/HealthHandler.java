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

package io.helidon.nima.observe.health;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import io.helidon.common.http.Http;
import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.nima.http.media.EntityWriter;
import io.helidon.nima.http.media.jsonp.JsonpMediaSupportProvider;
import io.helidon.nima.webserver.HtmlEncoder;
import io.helidon.nima.webserver.http.Handler;
import io.helidon.nima.webserver.http.ServerRequest;
import io.helidon.nima.webserver.http.ServerResponse;

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
                  Map<String, HealthCheck> checksByPath) {
        this.entityWriter = entityWriter;
        this.details = details;
        this.checks = checksByPath.values();
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        Map<String, HealthCheckResponse> responses = new LinkedHashMap<>();
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
            responses.put(check.name(), response);

            if (response.status() == HealthCheckResponse.Status.ERROR) {
                status = HealthCheckResponse.Status.ERROR;
            } else if (response.status() == HealthCheckResponse.Status.DOWN && status == HealthCheckResponse.Status.UP) {
                status = HealthCheckResponse.Status.DOWN;
            }
        }

        Http.Status responseStatus = switch (status) {
            case UP -> details ? Http.Status.OK_200 : Http.Status.NO_CONTENT_204;
            case DOWN -> Http.Status.SERVICE_UNAVAILABLE_503;
            case ERROR -> Http.Status.INTERNAL_SERVER_ERROR_500;
        };

        res.status(responseStatus);

        if (details) {
            entityWriter.write(JsonpMediaSupportProvider.JSON_OBJECT_TYPE,
                               toJson(status, responses),
                               res.outputStream(),
                               req.headers(),
                               res.headers());

        } else {
            res.send();
        }
    }

    private static JsonObject toJson(HealthCheckResponse.Status status, Map<String, HealthCheckResponse> responses) {
        JsonObjectBuilder response = HealthHelper.JSON.createObjectBuilder();
        response.add("status", status.toString());

        JsonArrayBuilder checks = HealthHelper.JSON.createArrayBuilder();
        responses.forEach((name, result) -> checks.add(HealthHelper.toJson(name, result)));

        response.add("checks", checks);
        return response.build();
    }
}
