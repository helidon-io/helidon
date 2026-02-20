/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.helidon.health.HealthCheck;
import io.helidon.health.HealthCheckResponse;
import io.helidon.http.HtmlEncoder;
import io.helidon.http.NotFoundException;
import io.helidon.http.Status;
import io.helidon.http.media.EntityWriter;
import io.helidon.json.JsonObject;
import io.helidon.webserver.http.Handler;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

class SingleCheckHandler implements Handler {
    private static final System.Logger LOGGER = System.getLogger(SingleCheckHandler.class.getName());

    private final EntityWriter<JsonObject> entityWriter;
    private final boolean details;
    private final List<HealthCheck> allChecks;
    private final Map<String, HealthCheck> checks;

    SingleCheckHandler(EntityWriter<JsonObject> entityWriter, boolean details, List<HealthCheck> checks) {
        this.entityWriter = entityWriter;
        this.details = details;
        this.allChecks = checks;
        this.checks = new HashMap<>();
    }

    @Override
    public void beforeStart() {
        allChecks.forEach(it -> this.checks.putIfAbsent(it.path(), it));
    }

    @Override
    public void handle(ServerRequest req, ServerResponse res) {
        String name = req.path().pathParameters().get("name");
        HealthCheck check = checks.get(name);
        if (check == null) {
            throw new NotFoundException(name);
        }

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

        Status responseStatus = switch (response.status()) {
            case UP -> details ? Status.OK_200 : Status.NO_CONTENT_204;
            case DOWN -> Status.SERVICE_UNAVAILABLE_503;
            case ERROR -> Status.INTERNAL_SERVER_ERROR_500;
        };

        res.status(responseStatus);

        if (details) {
            try (OutputStream out = res.outputStream()) {
                entityWriter.write(HealthHandler.JSON_OBJECT_TYPE,
                                   HealthHelper.toJson(check.name(), response),
                                   out,
                                   req.headers(),
                                   res.headers());
            } catch (IOException e) {
                LOGGER.log(System.Logger.Level.TRACE, "Failed to write health check response", e);
                res.status(Status.INTERNAL_SERVER_ERROR_500)
                        .send();
            }
        } else {
            res.send();
        }
    }
}
